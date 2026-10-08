package com.group2.fse.mobile_bff.client;

import com.group2.fse.mobile_bff.dto.MobileRecentTransaction;
import com.group2.fse.mobile_bff.dto.MobileTimelineResponse;
import com.group2.fse.mobile_bff.dto.MobileTimelineStage;
import com.group2.fse.mobile_bff.exception.ApiException;
import com.group2.fse.mobile_bff.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
public class TransactionServiceClient {

    private final RestTemplate restTemplate;
    private final String transactionServiceBaseUrl;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public TransactionServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.transaction.base-url:http://localhost:8090}") String transactionServiceBaseUrl,
            JwtTokenProvider jwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.transactionServiceBaseUrl = transactionServiceBaseUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public List<MobileRecentTransaction> fetchRecentTransactions(Long customerId, String bearerToken, String correlationId) {
        List<MobileRecentTransaction> list = new ArrayList<>();
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(transactionServiceBaseUrl)
                    .pathSegment("internal", "v1", "transactions")
                    .queryParam("customerId", customerId)
                    .queryParam("limit", 5)
                    .build()
                    .encode()
                    .toUriString();
            log.debug("Fetching recent transactions from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set("X-Correlation-Id", correlationId);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                for (Map<String, Object> map : response.getBody()) {
                    list.add(mapToRecentTransaction(map));
                }
            }
        } catch (Exception ex) {
            log.info("Could not fetch recent transactions from transaction-service (service may be initializing or empty): {}", ex.getMessage());
        }
        return list;
    }

    public MobileTimelineResponse fetchTimeline(String identifier, String bearerToken, String correlationId) {
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(transactionServiceBaseUrl)
                    .pathSegment("api", "v1", "transactions", identifier, "timeline")
                    .queryParam("view", "CUSTOMER")
                    .build()
                    .encode()
                    .toUriString();
            log.debug("Fetching timeline from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (bearerToken != null && !bearerToken.isBlank()) {
                headers.set("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken);
            } else {
                headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
            }
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set("X-Correlation-Id", correlationId);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return mapToTimelineResponse(response.getBody(), identifier);
            }
        } catch (HttpStatusCodeException ex) {
            log.warn("Downstream timeline query failed with status [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Transaction not found for identifier: " + identifier);
            }
            throw new ApiException(HttpStatus.valueOf(ex.getStatusCode().value()), "Unable to retrieve timeline: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to connect to transaction-service for timeline [{}]: {}", identifier, ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Transaction service temporarily unavailable");
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Timeline not found for: " + identifier);
    }

    public Map<String, Object> fetchTransactionDetail(String identifier, String bearerToken, String correlationId) {
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(transactionServiceBaseUrl)
                    .pathSegment("api", "v1", "transactions", identifier)
                    .build()
                    .encode()
                    .toUriString();
            log.debug("Fetching transaction detail from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set("X-Correlation-Id", correlationId);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ex) {
            log.debug("Could not fetch transaction detail for [{}]: {}", identifier, ex.getMessage());
        }
        return null;
    }

    public Map<String, Object> initiateTransfer(Map<String, Object> transferRequest, String idempotencyKey, String bearerToken, String correlationId) {
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(transactionServiceBaseUrl)
                    .pathSegment("api", "v1", "transactions", "transfers")
                    .build()
                    .encode()
                    .toUriString();
            log.debug("Initiating transfer at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (bearerToken != null && !bearerToken.isBlank()) {
                headers.set("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken);
            } else {
                headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                headers.set("Idempotency-Key", idempotencyKey);
            }
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set("X-Correlation-Id", correlationId);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(transferRequest, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (HttpStatusCodeException ex) {
            log.warn("Transfer initiation downstream error [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ApiException(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Failed to connect to transaction-service for transfer: {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Transaction service temporarily unavailable");
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Transfer initiation failed");
    }

    private MobileRecentTransaction mapToRecentTransaction(Map<String, Object> map) {
        String txId = map.get("transactionId") != null ? map.get("transactionId").toString() : "";
        String refNo = map.get("referenceNo") != null ? map.get("referenceNo").toString() : txId;
        String dir = map.get("direction") != null ? map.get("direction").toString() : "OUT";
        String curr = map.get("currency") != null ? map.get("currency").toString() : "PHP";
        String stat = map.get("status") != null ? map.get("status").toString() : "POSTED";
        String remarks = map.get("remarks") != null ? map.get("remarks").toString() : "";
        String date = map.get("createdAt") != null ? map.get("createdAt").toString() : "";
        if (date.isBlank() && map.get("submittedAt") != null) {
            date = map.get("submittedAt").toString();
        }

        BigDecimal amtVal = BigDecimal.ZERO;
        if (map.get("amount") != null) {
            try {
                amtVal = new BigDecimal(map.get("amount").toString());
            } catch (Exception ignored) {}
        }

        String prefix = "OUT".equalsIgnoreCase(dir) ? "-" : "+";
        String symbol = "PHP".equalsIgnoreCase(curr) ? "₱" : "$";
        String formattedAmount = prefix + symbol + String.format(Locale.US, "%,.2f", amtVal);

        return MobileRecentTransaction.builder()
                .transactionId(txId)
                .referenceNo(refNo)
                .trackingReference(refNo)
                .direction(dir)
                .amount(amtVal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                .formattedAmount(formattedAmount)
                .currency(curr)
                .status(stat)
                .remarks(remarks)
                .submittedAt(date)
                .createdAt(date)
                .build();
    }

    @SuppressWarnings("unchecked")
    private MobileTimelineResponse mapToTimelineResponse(Map<String, Object> body, String identifier) {
        String txId = body.get("transactionId") != null ? body.get("transactionId").toString() : identifier;
        String refNo = body.get("trackingReference") != null ? body.get("trackingReference").toString() :
                (body.get("referenceNo") != null ? body.get("referenceNo").toString() : txId);
        String stat = body.get("status") != null ? body.get("status").toString() : "PROCESSING";
        String overallStat = body.get("overallStatus") != null ? body.get("overallStatus").toString() : stat;
        String view = body.get("view") != null ? body.get("view").toString() : "CUSTOMER";
        Integer curStage = body.get("currentStage") != null ? Integer.valueOf(body.get("currentStage").toString()) : 1;
        Integer totStages = body.get("totalStages") != null ? Integer.valueOf(body.get("totalStages").toString()) : 3;
        String failureMsg = body.get("failureMessage") != null ? body.get("failureMessage").toString() : null;

        List<MobileTimelineStage> stages = new ArrayList<>();
        Object rawStages = body.get("stages");
        if (rawStages instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> sm) {
                    Map<String, Object> stageMap = (Map<String, Object>) sm;
                    Integer sNum = stageMap.get("stageNumber") != null ? Integer.valueOf(stageMap.get("stageNumber").toString()) :
                            (stageMap.get("stageNo") != null ? Integer.valueOf(stageMap.get("stageNo").toString()) : 1);
                    String sKey = stageMap.get("stageKey") != null ? stageMap.get("stageKey").toString() :
                            (stageMap.get("stageName") != null ? stageMap.get("stageName").toString() : "UNKNOWN");
                    String sName = stageMap.get("stageName") != null ? stageMap.get("stageName").toString() : sKey;
                    String title = stageMap.get("title") != null ? stageMap.get("title").toString() : "";
                    String subtitle = stageMap.get("subtitle") != null ? stageMap.get("subtitle").toString() : null;
                    String service = stageMap.get("service") != null ? stageMap.get("service").toString() : null;
                    String sStat = stageMap.get("status") != null ? stageMap.get("status").toString() : "PENDING";
                    String outcome = stageMap.get("outcome") != null ? stageMap.get("outcome").toString() : sStat;
                    String ts = stageMap.get("timestamp") != null ? stageMap.get("timestamp").toString() :
                            (stageMap.get("occurredAt") != null ? stageMap.get("occurredAt").toString() : null);
                    String detail = stageMap.get("details") != null ? stageMap.get("details").toString() :
                            (stageMap.get("detail") != null ? stageMap.get("detail").toString() : null);

                    stages.add(MobileTimelineStage.builder()
                            .stageNumber(sNum)
                            .stageNo(sNum)
                            .stageKey(sKey)
                            .stageName(sName)
                            .title(title)
                            .subtitle(subtitle)
                            .service(service)
                            .status(sStat)
                            .outcome(outcome)
                            .timestamp(ts)
                            .occurredAt(ts)
                            .details(detail)
                            .build());
                }
            }
        }

        return MobileTimelineResponse.builder()
                .transactionId(txId)
                .referenceNo(refNo)
                .trackingReference(refNo)
                .status(stat)
                .overallStatus(overallStat)
                .view(view)
                .currentStage(curStage)
                .totalStages(totStages)
                .stages(stages)
                .failureMessage(failureMsg)
                .build();
    }
}
