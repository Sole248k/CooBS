package com.group2.fse.risk_engine.benchmark;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.group2.fse.risk_engine.dto.RiskEvaluationRequest;
import com.group2.fse.risk_engine.dto.RiskEvaluationResponse;
import com.group2.fse.risk_engine.service.LimitPolicy;
import com.group2.fse.risk_engine.service.RiskEvaluator;
import com.group2.fse.risk_engine.service.VelocityCounter;

import lombok.extern.slf4j.Slf4j;

/**
 * FIN-406 Verification & Benchmark Test Suite.
 * Verifies:
 * 1. High-risk fraud rejection (score > 0.85 -> DECLINE with HIGH_RISK).
 * 2. KYC limit breach rejection (spend + amount > limit -> DECLINE with LIMIT_EXCEEDED).
 * 3. Zero-Lock property: on decline, no state mutation or ledger hold is created.
 * 4. Latency SLA: 100-iteration performance benchmark asserting p95 < 40ms.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class FraudRejectionAndSlaBenchmarkTest {

    private LimitPolicy limitPolicy;

    @Mock
    private VelocityCounter velocityCounter;

    private RiskEvaluator riskEvaluator;

    @BeforeEach
    void setUp() {
        limitPolicy = new LimitPolicy();
        riskEvaluator = new RiskEvaluator(limitPolicy, velocityCounter);
        org.mockito.Mockito.lenient().when(velocityCounter.reserve(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("FIN-406: High-Risk transfer (S > 0.85) must be DECLINED with HIGH_RISK & zero ledger side-effects")
    void testHighRiskFraudRejection() {
        // Given: TIER_1 customer with 4 burst transfers in last 10 minutes transferring max daily limit
        when(velocityCounter.getVelocityState(101L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(4)
                        .usedToday(BigDecimal.ZERO)
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-highrisk-audit-01")
                .customerId(101L)
                .amount(new BigDecimal("5000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_1_BASIC")
                .dailyLimit(new BigDecimal("5000.00"))
                .isNewBeneficiary(true)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then: Must be DECLINED with HIGH_RISK
        assertNotNull(response);
        assertEquals("DECLINE", response.getDecision(), "Decision must be DECLINE");
        assertEquals("FAILED", response.getFraudCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().contains("HIGH_RISK"), "Reason must contain HIGH_RISK");
        assertTrue(response.getFraudCheck().getReasons().contains("BURST_VELOCITY_LIMIT_EXCEEDED"));
        assertTrue(Double.parseDouble(response.getScore()) > 0.85, "Score must exceed 0.85 threshold");

        // Zero-Lock Proof: Never records approved transfer in Redis/Ledger
        verify(velocityCounter, never()).reserve(any(), any(), any(), any());
        log.info("[FIN-406] High-risk rejection verified. Score: {}, Reasons: {}", response.getScore(), response.getFraudCheck().getReasons());
    }

    @Test
    @DisplayName("FIN-406: Limit breach must be DECLINED with LIMIT_EXCEEDED & zero ledger side-effects")
    void testLimitExceededRejection() {
        // Given: Customer has already spent 48,000 against a 50,000 limit, attempting 5,000 transfer
        when(velocityCounter.getVelocityState(202L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(1)
                        .usedToday(new BigDecimal("48000.00"))
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-limit-audit-01")
                .customerId(202L)
                .amount(new BigDecimal("5000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .isNewBeneficiary(false)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then: Must be DECLINED with LIMIT_EXCEEDED
        assertNotNull(response);
        assertEquals("DECLINE", response.getDecision(), "Decision must be DECLINE");
        assertEquals("FAILED", response.getLimitCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().contains("LIMIT_EXCEEDED"), "Must flag LIMIT_EXCEEDED");
        assertTrue(response.getFraudCheck().getReasons().contains("TIER_DAILY_CEILING_REACHED"));

        // Zero-Lock Proof: Never records approved transfer
        verify(velocityCounter, never()).reserve(any(), any(), any(), any());
        log.info("[FIN-406] Limit breach rejection verified. Used: 48,000 + Req: 5,000 > Limit: 50,000. Reasons: {}",
                response.getFraudCheck().getReasons());
    }

    @Test
    @DisplayName("FIN-406: 100-Iteration Latency Benchmark must prove p95 < 40ms SLA")
    void testP95LatencySlaBenchmark() {
        // Given: Mock velocity counter state
        when(velocityCounter.getVelocityState(any())).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(2)
                        .usedToday(new BigDecimal("10000.00"))
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest benchmarkRequest = RiskEvaluationRequest.builder()
                .transactionId("tx-bench-01")
                .customerId(303L)
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .isNewBeneficiary(false)
                .build();

        // Warm-up JIT compiler (10 runs)
        for (int i = 0; i < 10; i++) {
            riskEvaluator.evaluate(benchmarkRequest);
        }

        // Measure 100 iterations
        int iterations = 100;
        List<Double> latenciesMs = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            long startNanos = System.nanoTime();
            RiskEvaluationResponse response = riskEvaluator.evaluate(benchmarkRequest);
            long endNanos = System.nanoTime();

            assertNotNull(response);
            double durationMs = (endNanos - startNanos) / 1_000_000.0;
            latenciesMs.add(durationMs);
        }

        // Calculate percentiles
        Collections.sort(latenciesMs);
        double p50 = latenciesMs.get((int) (iterations * 0.50));
        double p90 = latenciesMs.get((int) (iterations * 0.90));
        double p95 = latenciesMs.get((int) (iterations * 0.95));
        double p99 = latenciesMs.get((int) (iterations * 0.99));
        double max = latenciesMs.get(iterations - 1);

        log.info("==========================================================");
        log.info("[FIN-406 BENCHMARK] 100 Iterations Execution Latency Summary:");
        log.info("  • P50 (Median) : {} ms", String.format("%.3f", p50));
        log.info("  • P90          : {} ms", String.format("%.3f", p90));
        log.info("  • P95 (SLA)    : {} ms (Target: < 40.0 ms)", String.format("%.3f", p95));
        log.info("  • P99          : {} ms", String.format("%.3f", p99));
        log.info("  • Max          : {} ms", String.format("%.3f", max));
        log.info("==========================================================");

        // Assert strictly against ticket requirement (p95 < 40ms)
        assertTrue(p95 < 40.0, String.format("P95 latency %.3f ms exceeded SLA threshold of 40.0 ms", p95));
        assertTrue(p99 < 40.0, String.format("P99 latency %.3f ms exceeded SLA threshold of 40.0 ms", p99));
    }
}
