package com.group2.fse.orchestration_service.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** risk-engine, R1 (H5). The caller treats any DownstreamException as RISK_UNAVAILABLE. */
@Component
public class RiskClient {

    public static final String APPROVE = "APPROVE";
    public static final String FAILED = "FAILED";

    private final InternalApi api;
    private final RestClient http;

    public RiskClient(InternalApi api,
            @Value("${app.clients.risk.base-url}") String baseUrl,
            @Value("${app.clients.risk.timeout}") Duration timeout) {
        this.api = api;
        this.http = api.client(baseUrl, timeout);
    }

    public Decision evaluate(Request request) {
        return api.call(() -> http.post()
                .uri("/internal/v1/risk/evaluate")
                .body(request)
                .retrieve()
                .body(Decision.class));
    }

    public record Request(
            UUID transactionId,
            Long customerId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String currency,
            String channel,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal customerRiskScore,
            String kycTier,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dailyLimit,
            @JsonProperty("isNewBeneficiary") boolean isNewBeneficiary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Decision(String decision, BigDecimal score, Check fraudCheck, Check limitCheck) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Check(String outcome, List<String> reasons) {

        public boolean failed() {
            return FAILED.equals(outcome);
        }
    }
}
