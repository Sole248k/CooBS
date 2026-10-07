package com.group2.fse.mobile_bff.client;

import com.group2.fse.mobile_bff.dto.MobileMemberSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

    public MobileMemberSummary fetchMemberProfile(Long customerId, String bearerToken, String correlationId) {
        try {
            String url = customerServiceBaseUrl + "/api/v1/customers/me";
            log.debug("Fetching customer profile from: {}", url);

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
                Map<String, Object> body = response.getBody();
                Long id = body.get("customerId") != null ? Long.valueOf(body.get("customerId").toString()) : customerId;
                String displayName = body.get("displayName") != null ? body.get("displayName").toString() : "";
                if (displayName.isBlank() && body.get("username") != null) {
                    displayName = body.get("username").toString();
                }
                String email = body.get("email") != null ? body.get("email").toString() : "";
                String kycStatus = body.get("kycStatus") != null ? body.get("kycStatus").toString() : "VERIFIED";

                return MobileMemberSummary.builder()
                        .customerId(id)
                        .displayName(displayName)
                        .email(email)
                        .kycStatus(kycStatus)
                        .build();
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch customer profile for customer [{}]: {}", customerId, ex.getMessage());
        }

        // Graceful fallback member
        return MobileMemberSummary.builder()
                .customerId(customerId)
                .displayName("Member")
                .email("")
                .kycStatus("VERIFIED")
                .build();
    }
}
