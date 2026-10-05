package com.group2.fse.risk_engine.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Handshake H5 Request payload for POST /internal/v1/risk/evaluate.
 * Frozen contract specification aligned with contracts/examples/R1-risk-evaluate.request.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskEvaluationRequest {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotNull(message = "customerId is required")
    private Long customerId;

    private String sourceAccountId;

    private String destinationAccountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "PHP";

    @Builder.Default
    private String channel = "APP";

    private BigDecimal customerRiskScore;

    @Builder.Default
    private String kycTier = "TIER_2_STANDARD";

    private BigDecimal dailyLimit;

    @Builder.Default
    private Boolean isNewBeneficiary = false;

    private Integer transfersInLast10Min;
}
