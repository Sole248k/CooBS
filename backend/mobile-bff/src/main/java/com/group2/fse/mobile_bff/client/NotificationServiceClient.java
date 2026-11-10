package com.group2.fse.mobile_bff.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

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

    public Optional<Integer> fetchUnreadCount(Long customerId, String bearerToken, String correlationId) {
        try {
            String url = notificationServiceBaseUrl + "/api/v1/notifications/unread-count";
            log.debug("Fetching unread notifications count from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (bearerToken != null && !bearerToken.isBlank()) {
                headers.set("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken);
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
                Object countObj = response.getBody().get("unreadCount");
                if (countObj instanceof Number num) {
                    return Optional.of(num.intValue());
                } else if (countObj != null) {
                    return Optional.of(Integer.parseInt(countObj.toString()));
                }
                return Optional.of(0);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch unread notification count for customer [{}]: {}", customerId, ex.getMessage());
        }
        return Optional.empty();
    }
}
