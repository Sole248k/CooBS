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
public class AuditServiceClient {

    private final RestTemplate restTemplate;
    private final String auditServiceBaseUrl;

    @Autowired
    public AuditServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.audit.base-url:http://localhost:8085}") String auditServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.auditServiceBaseUrl = auditServiceBaseUrl;
    }

    public Map<String, Object> fetchAccountStatement(Long accountId, int page, int size, String bearerToken, String correlationId) {
        try {
            String url = auditServiceBaseUrl + "/api/v1/audit/accounts/" + accountId + "/statement?page=" + page + "&size=" + size;
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to fetch audit statement for account {}: {}", accountId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> verifyChainIntegrity(Long accountId, String bearerToken, String correlationId) {
        try {
            String url = auditServiceBaseUrl + "/api/v1/audit/verify-chain/" + accountId;
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to verify chain for account {}: {}", accountId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> fetchDailySeal(String businessDate, String bearerToken, String correlationId) {
        try {
            String url = auditServiceBaseUrl + "/api/v1/audit/seals/" + businessDate;
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to fetch daily seal for date {}: {}", businessDate, e.getMessage());
            return Collections.emptyMap();
        }
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
