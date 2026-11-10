package com.group2.fse.transaction_service.interbank.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Partner adapter for AuraBank (Group 3 CBS) implementing the wire protocol specified in
 * docs/integrated/group2-interbank-endpoint.json.
 *
 * Inward path: POST /api/v1/interbank/transfers/inward
 * Authentication: Header X-API-Key and X-Interbank-API-Key with pre-shared key
 * Response: status COMPLETED / REJECTED / PENDING, referenceNumber
 */
@Slf4j
@Component
public class AuraBankPartnerAdapter implements PartnerAdapter {

    private static final String INWARD_PATH = "/api/v1/interbank/transfers/inward";
    private static final String DEFAULT_SENDER_NAME = "CooBS Member";
    private static final String DEFAULT_BENEFICIARY_NAME = "AuraBank Customer";
    private static final int MAX_PURPOSE = 140;

    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final HttpClient http;

    public AuraBankPartnerAdapter(ObjectMapper objectMapper,
            @Value("${app.interbank.outbound.partner-timeout-ms:5000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(2000, timeoutMs / 2)))
                .build();
    }

    @Override
    public PartnerAnswer send(OutboundTransfer t) {
        String body;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageId", t.messageId().toString());
            payload.put("sourceBankCode", t.ownBankCode());
            payload.put("destinationBankCode", t.partner().getBankCode());

            Map<String, String> sender = new LinkedHashMap<>();
            sender.put("accountNumber", t.senderAccountNumber());
            sender.put("name", DEFAULT_SENDER_NAME);
            payload.put("sender", sender);

            Map<String, String> beneficiary = new LinkedHashMap<>();
            beneficiary.put("accountNumber", t.beneficiaryAccountNumber());
            beneficiary.put("name", t.beneficiaryName() != null && !t.beneficiaryName().isBlank()
                    ? t.beneficiaryName() : DEFAULT_BENEFICIARY_NAME);
            payload.put("beneficiary", beneficiary);

            payload.put("amount", t.amount().doubleValue());
            payload.put("currency", t.currency());
            payload.put("purpose", purpose(t.purpose()));
            payload.put("senderReference", t.senderReference() != null ? t.senderReference() : ("TX-" + t.messageId()));

            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to build AuraBank wire payload for {}: {}", t.messageId(), e.getMessage());
            return PartnerAnswer.unknown();
        }

        String apiKey = t.partner().getSharedSecret();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "AURABANK-SECURE-KEY-2026";
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base(t) + INWARD_PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-API-Key", apiKey)
                .header("X-Interbank-API-Key", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        return exchange(t, builder.build(), "send");
    }

    @Override
    public PartnerAnswer status(OutboundTransfer t) {
        // AuraBank OpenAPI spec does not specify a GET status endpoint.
        // We attempt query on /api/v1/interbank/transfers/{id} if supported by partner.
        String apiKey = t.partner().getSharedSecret();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "AURABANK-SECURE-KEY-2026";
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base(t) + "/api/v1/interbank/transfers/" + t.messageId()))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("X-API-Key", apiKey)
                .header("X-Interbank-API-Key", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return exchange(t, request, "status");
    }

    private PartnerAnswer exchange(OutboundTransfer t, HttpRequest request, String what) {
        String bank = t.partner().getBankCode();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PartnerAnswer.unknown();
        } catch (Exception e) {
            if (neverConnected(e)) {
                log.warn("{} could not be reached for {} of message {}; nothing was sent: {}",
                        bank, what, t.messageId(), e.toString());
                return PartnerAnswer.unreachable();
            }
            log.warn("{} did not answer the {} of message {}: {}", bank, what, t.messageId(), e.toString());
            return PartnerAnswer.unknown();
        }

        if (response.statusCode() >= 500) {
            log.warn("{} answered {} to the {} of message {}", bank, response.statusCode(), what, t.messageId());
            return PartnerAnswer.unknown();
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() == 200) {
                String status = root.path("status").asText("").trim().toUpperCase();
                String ref = root.hasNonNull("referenceNumber")
                        ? root.path("referenceNumber").asText()
                        : root.path("receiverReference").asText("INW-" + t.messageId());

                if ("COMPLETED".equals(status) || "ACCEPTED".equals(status)) {
                    return PartnerAnswer.accepted(ref);
                } else if ("REJECTED".equals(status)) {
                    String reason = root.path("reasonCode").asText("REJECTED_BY_PARTNER");
                    return PartnerAnswer.rejected(reason);
                } else if ("PENDING".equals(status)) {
                    return PartnerAnswer.unknown();
                }
            } else if (response.statusCode() == 404 && "status".equals(what)) {
                return PartnerAnswer.notFound();
            } else if (response.statusCode() == 400 || response.statusCode() == 404 || response.statusCode() == 422) {
                String errorMsg = root.hasNonNull("message") ? root.path("message").asText() : "HTTP_" + response.statusCode();
                return PartnerAnswer.rejected(errorMsg);
            }
        } catch (Exception e) {
            log.warn("{} answered {} with unreadable body to {} of message {}", bank, response.statusCode(), what, t.messageId());
            return PartnerAnswer.unknown();
        }

        return PartnerAnswer.unknown();
    }

    private static boolean neverConnected(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException
                    || cause instanceof UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }

    private static String base(OutboundTransfer t) {
        String url = t.partner().getBaseUrl().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String purpose(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return "Interbank Transfer";
        }
        return remarks.length() <= MAX_PURPOSE ? remarks : remarks.substring(0, MAX_PURPOSE);
    }
}
