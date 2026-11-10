package com.group2.fse.staff_bff.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class TransactionServiceClient {

    private final RestTemplate restTemplate;
    private final String transactionServiceBaseUrl;

    @Autowired
    public TransactionServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.transaction.base-url:http://localhost:8090}") String transactionServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.transactionServiceBaseUrl = transactionServiceBaseUrl;
    }

    public Map<String, Object> executeAssistedTransfer(Map<String, Object> payload, String idempotencyKey, String bearerToken, String correlationId) {
        String url = transactionServiceBaseUrl + "/api/v1/transactions/transfers";
        String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : java.util.UUID.randomUUID().toString();
        HttpHeaders headers = createHeaders(bearerToken, correlationId, effectiveIdempotencyKey);
        Map<String, Object> effectivePayload = new java.util.HashMap<>(payload);
        if (!effectivePayload.containsKey("destinationBankCode") || effectivePayload.get("destinationBankCode") == null) {
            effectivePayload.put("destinationBankCode", "COOBS");
        }
        if (!effectivePayload.containsKey("currency") || effectivePayload.get("currency") == null) {
            effectivePayload.put("currency", "PHP");
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(effectivePayload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> fetchTransactionSummary(String transactionId, String bearerToken, String correlationId) {
        try {
            String url = transactionServiceBaseUrl + "/api/v1/transactions/" + transactionId;
            HttpHeaders headers = createHeaders(bearerToken, correlationId, null);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to fetch transaction summary for {}: {}", transactionId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> fetchStaffTimeline(String transactionId, String bearerToken, String correlationId) {
        try {
            String url = transactionServiceBaseUrl + "/api/v1/transactions/" + transactionId + "/timeline?view=staff";
            HttpHeaders headers = createHeaders(bearerToken, correlationId, null);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to fetch staff timeline for {}: {}", transactionId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> requestReversal(String transactionId, String reason, String idempotencyKey, String bearerToken, String correlationId) {
        String url = transactionServiceBaseUrl + "/api/v1/transactions/" + transactionId + "/reversal";
        String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : java.util.UUID.randomUUID().toString();
        HttpHeaders headers = createHeaders(bearerToken, correlationId, effectiveIdempotencyKey);
        Map<String, String> payload = Map.of("reason", reason != null ? reason : "Branch customer counter dispute");
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    private HttpHeaders createHeaders(String bearerToken, String correlationId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = bearerToken;
        if ((token == null || token.isBlank()) && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null) {
            Object creds = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getCredentials();
            if (creds instanceof String s && !s.isBlank()) {
                token = s;
            }
        }
        if (token != null && !token.isBlank()) {
            headers.set("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set("X-Correlation-Id", correlationId);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }
}
