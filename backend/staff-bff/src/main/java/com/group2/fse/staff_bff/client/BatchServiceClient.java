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
public class BatchServiceClient {

    private final RestTemplate restTemplate;
    private final String batchServiceBaseUrl;

    @Autowired
    public BatchServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.batch.base-url:http://localhost:8093}") String batchServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.batchServiceBaseUrl = batchServiceBaseUrl;
    }

    public Map<String, Object> triggerEodBatch(Map<String, Object> requestPayload, String bearerToken, String correlationId) {
        String url = batchServiceBaseUrl + "/api/v1/batch/eod/trigger";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> fetchBatchRun(Long batchRunId, String bearerToken, String correlationId) {
        try {
            String url = batchServiceBaseUrl + "/api/v1/batch/eod/runs/" + batchRunId;
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to fetch batch run for ID {}: {}", batchRunId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public List<Map<String, Object>> fetchAllBatchRuns(String bearerToken, String correlationId) {
        try {
            String url = batchServiceBaseUrl + "/api/v1/batch/eod/runs";
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch all batch runs: {}", e.getMessage());
            return Collections.emptyList();
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
