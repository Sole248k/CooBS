package com.group2.fse.risk_engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for POST /internal/v1/risk/outcomes (R2): what became of a transfer the
 * engine approved. Aligned with contracts/examples/R2-risk-outcome.request.json and
 * R2-risk-outcome-reversed.request.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskOutcomeRequest {

    public static final String FAILED = "FAILED";
    public static final String REVERSED = "REVERSED";

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    // FAILED: approved here, but the transfer did not post, so its reservation is given back.
    // REVERSED: the transfer posted and was reversed afterwards; it counts as one of the
    // customer's reversals for the day, and its reservation stays.
    @NotBlank(message = "outcome is required")
    @Pattern(regexp = "FAILED|REVERSED", message = "outcome must be FAILED or REVERSED")
    private String outcome;

    // The customer who sent the transfer. Needed for REVERSED: a transfer can be reversed long
    // after the engine has forgotten its reservation.
    private Long customerId;

    @JsonIgnore
    @AssertTrue(message = "customerId is required when outcome is REVERSED")
    public boolean isCustomerGivenForAReversal() {
        return !REVERSED.equals(outcome) || customerId != null;
    }
}
