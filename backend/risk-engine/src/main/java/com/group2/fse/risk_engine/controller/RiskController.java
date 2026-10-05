package com.group2.fse.risk_engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.group2.fse.risk_engine.dto.RiskEvaluationRequest;
import com.group2.fse.risk_engine.dto.RiskEvaluationResponse;
import com.group2.fse.risk_engine.service.RiskEvaluator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal Risk Evaluation Endpoint (Handshake H5, Endpoint R1).
 * Called by Carl (Dev 1)'s orchestration-service Transfer Saga.
 * Target SLA: p95 latency < 40ms.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskEvaluator riskEvaluator;

    @PostMapping("/evaluate")
    public ResponseEntity<RiskEvaluationResponse> evaluateRisk(@Valid @RequestBody RiskEvaluationRequest request) {
        log.info("Evaluating risk for transactionId: {}, customerId: {}, amount: {} {}",
                request.getTransactionId(), request.getCustomerId(), request.getAmount(), request.getCurrency());

        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        log.info("Risk evaluation completed for transactionId: {}. Decision: {}, Score: {}, Duration: {}ms",
                request.getTransactionId(), response.getDecision(), response.getScore(), response.getEvaluatedInMs());

        return ResponseEntity.ok(response);
    }
}
