package com.group2.fse.transaction_service.interbank.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferRequest;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferResponse;
import com.group2.fse.transaction_service.interbank.security.InterbankAuthValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * A partner that follows INTERBANK_TRANSFER_PROPOSAL.md: the same two endpoints we expose
 * ourselves (I1 and I2), signed with the secret shared with that partner.
 *
 * The request is signed as the proposal says: X-Signature is the HMAC-SHA256, in hex, of
 * "<X-Timestamp>.<body>" with the shared secret, and X-Bank-Code is our own bank code. A partner
 * that has agreed on the payload but not on signing has no shared secret here and is sent the
 * same message unsigned.
 */
@Slf4j
@Component
public class ProposalPartnerAdapter implements PartnerAdapter {

    private static final String TRANSFERS = "/api/v1/interbank/transfers";
    private static final String SENDER_NAME = "CooBS Member";
    private static final int MAX_PURPOSE = 140;

    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final HttpClient http;

    public ProposalPartnerAdapter(ObjectMapper objectMapper,
            @Value("${app.interbank.outbound.partner-timeout-ms:5000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public PartnerAnswer send(OutboundTransfer t) {
        String body;
        try {
            body = objectMapper.writeValueAsString(InterbankTransferRequest.builder()
                    .messageId(t.messageId().toString())
                    .sourceBankCode(t.ownBankCode())
                    .destinationBankCode(t.partner().getBankCode())
                    .sender(InterbankTransferRequest.Party.builder()
                            .accountNumber(t.senderAccountNumber()).name(SENDER_NAME).build())
                    .beneficiary(InterbankTransferRequest.Beneficiary.builder()
                            .accountNumber(t.beneficiaryAccountNumber()).name(t.beneficiaryName()).build())
                    .amount(t.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                    .currency(t.currency())
                    .purpose(purpose(t.purpose()))
                    .senderReference(t.senderReference())
                    .createdAt(t.createdAt().toString())
                    .build());
        } catch (Exception e) {
            // Nothing has left this service. The caller keeps asking; the cause is in the log.
            log.error("Message {} for {} could not be built: {}", t.messageId(), t.partner().getBankCode(), e.getMessage());
            return PartnerAnswer.unknown();
        }
        return exchange(t, signed(t, body).uri(URI.create(base(t) + TRANSFERS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), "send");
    }

    @Override
    public PartnerAnswer status(OutboundTransfer t) {
        return exchange(t, signed(t, "").uri(URI.create(base(t) + TRANSFERS + "/" + t.messageId()))
                .GET().build(), "status");
    }

    /**
     * A partner we share a secret with gets the proposal's timestamp and signature. A partner
     * with no secret configured takes plain JSON, so the request goes out unsigned; our bank
     * code is still named.
     */
    private HttpRequest.Builder signed(OutboundTransfer t, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("X-Bank-Code", t.ownBankCode());
        String secret = t.partner().getSharedSecret();
        if (secret == null || secret.isBlank()) {
            return request;
        }
        String timestamp = Instant.now().toString();
        return request
                .header("X-Timestamp", timestamp)
                .header("X-Signature", InterbankAuthValidator.computeHmacSha256Hex(secret, timestamp + "." + body));
    }

    /**
     * The partner answers ACCEPTED, REJECTED or (from its status endpoint) NOT_FOUND in the body,
     * whatever the HTTP status. A 5xx, a timeout, or a body that says none of these is no answer.
     */
    private PartnerAnswer exchange(OutboundTransfer t, HttpRequest request, String what) {
        String bank = t.partner().getBankCode();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PartnerAnswer.unknown();
        } catch (Exception e) {
            log.warn("{} did not answer the {} of message {}: {}", bank, what, t.messageId(), e.toString());
            return PartnerAnswer.unknown();
        }
        if (response.statusCode() >= 500) {
            log.warn("{} answered {} to the {} of message {}", bank, response.statusCode(), what, t.messageId());
            return PartnerAnswer.unknown();
        }
        InterbankTransferResponse answer;
        try {
            answer = objectMapper.readValue(response.body(), InterbankTransferResponse.class);
        } catch (Exception e) {
            log.warn("{} answered {} with a body that cannot be read to the {} of message {}",
                    bank, response.statusCode(), what, t.messageId());
            return PartnerAnswer.unknown();
        }
        String status = answer == null || answer.getStatus() == null ? "" : answer.getStatus().trim().toUpperCase();
        return switch (status) {
            case "ACCEPTED" -> PartnerAnswer.accepted(answer.getReceiverReference());
            case "REJECTED" -> PartnerAnswer.rejected(answer.getReasonCode());
            case "NOT_FOUND" -> PartnerAnswer.notFound();
            default -> {
                log.warn("{} answered {} with status '{}' to the {} of message {}",
                        bank, response.statusCode(), status, what, t.messageId());
                yield PartnerAnswer.unknown();
            }
        };
    }

    private static String base(OutboundTransfer t) {
        String url = t.partner().getBaseUrl().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String purpose(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return null;
        }
        return remarks.length() <= MAX_PURPOSE ? remarks : remarks.substring(0, MAX_PURPOSE);
    }
}
