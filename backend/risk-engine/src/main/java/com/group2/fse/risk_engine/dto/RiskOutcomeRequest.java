package com.group2.fse.risk_engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for POST /internal/v1/risk/outcomes (R2): what became of a transfer the
 * engine approved. Aligned with contracts/examples/R2-risk-outcome.request.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskOutcomeRequest {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    // FAILED: approved here, but the transfer did not post, so its reservation is given back.
    @NotBlank(message = "outcome is required")
    @Pattern(regexp = "FAILED", message = "outcome must be FAILED")
    private String outcome;
}
