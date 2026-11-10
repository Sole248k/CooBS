package com.group2.fse.risk_engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload for POST /internal/v1/risk/outcomes (R2).
 * Aligned with contracts/examples/R2-risk-outcome.response.json and
 * R2-risk-outcome-reversed.response.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskOutcomeResponse {

    private String transactionId;
    private String outcome;

    // False when the transaction held no reservation: it was never approved, or was released before.
    private boolean released;

    // Present only when something was given back. Money is a string.
    private String releasedAmount;

    // Present only for REVERSED: how many of the customer's transfers have been reversed today.
    private Integer reversalsToday;
}
