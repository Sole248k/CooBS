package com.group2.fse.risk_engine.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Handshake H5 Response payload for POST /internal/v1/risk/evaluate.
 * Frozen contract specification aligned with contracts/examples/R1-risk-evaluate.response.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskEvaluationResponse {

    private String evaluationId;

    private String transactionId;

    private String decision;

    private String score;

    private Double riskScore;

    private FraudCheckResult fraudCheck;

    private LimitCheckResult limitCheck;

    private List<String> reasons;

    private Instant evaluatedAt;

    private long evaluatedInMs;

    private Long executionDurationMs;
}
