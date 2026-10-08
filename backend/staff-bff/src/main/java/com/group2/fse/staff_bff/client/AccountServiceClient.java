package com.group2.fse.staff_bff.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AccountServiceClient {

    private final RestTemplate restTemplate;
    private final String accountServiceBaseUrl;

    @Autowired
    public AccountServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.account.base-url:http://localhost:8082}") String accountServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.accountServiceBaseUrl = accountServiceBaseUrl;
    }

    public List<Map<String, Object>> fetchAccountsByCustomerId(Long customerId, String bearerToken, String correlationId) {
        try {
            String url = accountServiceBaseUrl + "/api/v1/accounts?customerId=" + customerId;
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            List<Map<String, Object>> list = response.getBody() != null ? response.getBody() : Collections.emptyList();
            return list.stream()
                    .filter(acc -> {
                        Object cid = acc.get("customerId");
                        return cid != null && customerId.toString().equals(cid.toString());
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch accounts for customer ID {}: {}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> fetchAllAccounts(String bearerToken, String correlationId) {
        try {
            String url = accountServiceBaseUrl + "/api/v1/accounts";
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch all accounts: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> fetchAccountFlags(Long accountId, String bearerToken, String correlationId) {
        try {
            String url = accountServiceBaseUrl + "/api/v1/accounts/" + accountId + "/flags";
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch flags for account ID {}: {}", accountId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> fetchPendingClosureRequests(String bearerToken, String correlationId) {
        try {
            String url = accountServiceBaseUrl + "/api/v1/accounts/closure-requests?status=PENDING";
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch pending closure requests: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> openAccount(Map<String, Object> requestPayload, String bearerToken, String correlationId) {
        String url = accountServiceBaseUrl + "/api/v1/accounts";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> updateAccountStatus(Long accountId, String status, String bearerToken, String correlationId) {
        String url = accountServiceBaseUrl + "/api/v1/accounts/" + accountId + "/status";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        Map<String, String> payload = Map.of("status", status);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.PATCH, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> addFlag(Long accountId, String reason, String bearerToken, String correlationId) {
        String url = accountServiceBaseUrl + "/api/v1/accounts/" + accountId + "/flags";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        Map<String, String> payload = Map.of("reason", reason);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> removeFlag(Long accountId, Long flagId, String bearerToken, String correlationId) {
        String url = accountServiceBaseUrl + "/api/v1/accounts/" + accountId + "/flags/" + flagId;
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> approveClosure(Long closureId, String bearerToken, String correlationId) {
        String url = accountServiceBaseUrl + "/api/v1/accounts/closure-requests/" + closureId + "/approve";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> rejectClosure(Long closureId, String reason, String bearerToken, String correlationId) {
        String url = accountServiceBaseUrl + "/api/v1/accounts/closure-requests/" + closureId + "/reject";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        Map<String, String> payload = Map.of("reason", reason != null ? reason : "Account balance non-zero or unresolved holds");
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    private HttpHeaders createHeaders(String bearerToken, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.set("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set("X-Correlation-Id", correlationId);
        }
        return headers;
    }
}
