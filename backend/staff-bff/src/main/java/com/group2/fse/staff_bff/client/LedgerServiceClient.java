package com.group2.fse.staff_bff.client;

import com.group2.fse.staff_bff.exception.ApiException;
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
        HttpHeaders headers = createHeaders(bearerToken, correlationId, requireKey(idempotencyKey));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> executeCashWithdrawal(Map<String, Object> requestPayload, String idempotencyKey, String bearerToken, String correlationId) {
        String url = ledgerServiceBaseUrl + "/api/v1/ledger/debits";
        HttpHeaders headers = createHeaders(bearerToken, correlationId, requireKey(idempotencyKey));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    /**
     * The key and the reference are the caller's: they are what makes a repeated click the same
     * operation. A key made up here would be new on every call, so a retry after a lost answer
     * would pay twice. A missing reference is refused by the ledger itself.
     */
    private static String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETERS",
                    "The Idempotency-Key header is required for a cash deposit or withdrawal.");
        }
        return idempotencyKey;
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
