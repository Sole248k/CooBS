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
public class CustomerServiceClient {

    private final RestTemplate restTemplate;
    private final String customerServiceBaseUrl;

    @Autowired
    public CustomerServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.customer.base-url:http://localhost:8089}") String customerServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.customerServiceBaseUrl = customerServiceBaseUrl;
    }

    public Map<String, Object> fetchCustomer360(Long customerId, String bearerToken, String correlationId) {
        try {
            String url = customerServiceBaseUrl + "/api/v1/customers/" + customerId + "/360";
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch customer 360 for ID {}: {}", customerId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> fetchCustomerProfile(Long customerId, String bearerToken, String correlationId) {
        try {
            String url = customerServiceBaseUrl + "/api/v1/customers/" + customerId;
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch customer profile for ID {}: {}", customerId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchPendingKycUpdateRequests(String bearerToken, String correlationId) {
        try {
            String url = customerServiceBaseUrl + "/api/v1/customers/kyc/update-requests?status=PENDING";
            HttpHeaders headers = createHeaders(bearerToken, correlationId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            if (response.getBody() != null) {
                Object content = response.getBody().get("content");
                if (content instanceof List<?>) {
                    return (List<Map<String, Object>>) content;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch pending KYC update requests: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> approveKycUpdateRequest(Long requestId, String bearerToken, String correlationId) {
        String url = customerServiceBaseUrl + "/api/v1/customers/kyc/update-requests/" + requestId + "/approve";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public Map<String, Object> rejectKycUpdateRequest(Long requestId, String reason, String bearerToken, String correlationId) {
        String url = customerServiceBaseUrl + "/api/v1/customers/kyc/update-requests/" + requestId + "/reject";
        HttpHeaders headers = createHeaders(bearerToken, correlationId);
        Map<String, String> payload = Map.of("rejectionReason", reason != null ? reason : "Insufficient documentation");
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
