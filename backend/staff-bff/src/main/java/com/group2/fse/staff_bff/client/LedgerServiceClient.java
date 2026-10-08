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
            String url = ledgerServiceBaseUrl + "/api/v1/ledger/balances/" + accountId;
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
        HttpHeaders headers = createHeaders(bearerToken, correlationId, idempotencyKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> executeCashWithdrawal(Map<String, Object> requestPayload, String idempotencyKey, String bearerToken, String correlationId) {
        String url = ledgerServiceBaseUrl + "/api/v1/ledger/debits";
        HttpHeaders headers = createHeaders(bearerToken, correlationId, idempotencyKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    private HttpHeaders createHeaders(String bearerToken, String correlationId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.set("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken);
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
