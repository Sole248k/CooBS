package com.group2.fse.orchestration_service.client;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** customer-service, C7: the risk profile the risk engine needs (H6). */
@Component
public class CustomerClient {

    private final InternalApi api;
    private final RestClient http;

    public CustomerClient(InternalApi api,
            @Value("${app.clients.customer.base-url}") String baseUrl,
            @Value("${app.clients.customer.timeout}") Duration timeout) {
        this.api = api;
        this.http = api.client(baseUrl, timeout);
    }

    public RiskProfile riskProfile(Long customerId) {
        return api.call(() -> http.get()
                .uri("/internal/v1/customers/{customerId}/risk-profile", customerId)
                .retrieve()
                .body(RiskProfile.class));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskProfile(BigDecimal riskScore, String kycTier, BigDecimal dailyLimit) {
    }
}
