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
public class NotificationServiceClient {

    private final RestTemplate restTemplate;
    private final String notificationServiceBaseUrl;

    @Autowired
    public NotificationServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.notification.base-url:http://localhost:8084}") String notificationServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.notificationServiceBaseUrl = notificationServiceBaseUrl;
    }

    public List<Map<String, Object>> fetchDlqMessages(String bearerToken, String correlationId) {
        try {
            String url = notificationServiceBaseUrl + "/api/v1/notifications/admin/dlq";
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch DLQ messages: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> replayDlqRecord(String recordId, String bearerToken, String correlationId) {
        String url = notificationServiceBaseUrl + "/api/v1/notifications/admin/dlq/" + recordId + "/replay";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

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
