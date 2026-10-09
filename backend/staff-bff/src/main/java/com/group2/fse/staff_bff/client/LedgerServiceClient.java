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
public class LedgerServiceClient {

    private final RestTemplate restTemplate;
    private final String ledgerServiceBaseUrl;

    @Autowired
    public LedgerServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.ledger.base-url:http://localhost:8083}") String ledgerServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.ledgerServiceBaseUrl = ledgerServiceBaseUrl;
    }

    public Map<String, Object> fetchBalance(Long accountId, String bearerToken, String correlationId) {
        try {
            String url = ledgerServiceBaseUrl + "/api/v1/ledger/accounts/" + accountId + "/balance";
            HttpHeaders headers = createHeaders(bearerToken, correlationId, null);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to fetch balance for account ID {}: {}", accountId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> executeCashDeposit(Map<String, Object> requestPayload, String idempotencyKey, String bearerToken, String correlationId) {
        String url = ledgerServiceBaseUrl + "/api/v1/ledger/credits";
        String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : java.util.UUID.randomUUID().toString();
        HttpHeaders headers = createHeaders(bearerToken, correlationId, effectiveIdempotencyKey);
        Map<String, Object> payloadWithRef = new java.util.HashMap<>(requestPayload);
        if (!payloadWithRef.containsKey("referenceNo") || payloadWithRef.get("referenceNo") == null || payloadWithRef.get("referenceNo").toString().isBlank()) {
            payloadWithRef.put("referenceNo", "DEP-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000));
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payloadWithRef, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> executeCashWithdrawal(Map<String, Object> requestPayload, String idempotencyKey, String bearerToken, String correlationId) {
        String url = ledgerServiceBaseUrl + "/api/v1/ledger/debits";
        String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : java.util.UUID.randomUUID().toString();
        HttpHeaders headers = createHeaders(bearerToken, correlationId, effectiveIdempotencyKey);
        Map<String, Object> payloadWithRef = new java.util.HashMap<>(requestPayload);
        if (!payloadWithRef.containsKey("referenceNo") || payloadWithRef.get("referenceNo") == null || payloadWithRef.get("referenceNo").toString().isBlank()) {
            payloadWithRef.put("referenceNo", "WDL-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000));
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payloadWithRef, headers);

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
