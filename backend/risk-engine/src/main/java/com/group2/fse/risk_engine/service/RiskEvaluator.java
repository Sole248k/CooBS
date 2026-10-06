package com.group2.fse.risk_engine.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.group2.fse.risk_engine.dto.FraudCheckResult;
import com.group2.fse.risk_engine.dto.LimitCheckResult;
import com.group2.fse.risk_engine.dto.RiskEvaluationRequest;
import com.group2.fse.risk_engine.dto.RiskEvaluationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEvaluator {

    public static final String DECISION_APPROVE = "APPROVE";
    public static final String DECISION_DECLINE = "DECLINE";

    public static final String OUTCOME_PASSED = "PASSED";
    public static final String OUTCOME_FAILED = "FAILED";

    public static final String REASON_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    public static final String REASON_HIGH_RISK = "HIGH_RISK";
    public static final String REASON_LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    public static final String REASON_TIER_DAILY_CEILING_REACHED = "TIER_DAILY_CEILING_REACHED";
    public static final String REASON_BURST_VELOCITY = "BURST_VELOCITY_LIMIT_EXCEEDED";
    public static final String REASON_VELOCITY_ELEVATED = "VELOCITY_ELEVATED_10MIN";
    public static final String REASON_REDIS_UNAVAILABLE = "REDIS_UNAVAILABLE";
    public static final String REASON_NEW_BENEFICIARY = "NEW_BENEFICIARY_FLAG";
    public static final String REASON_EXCESSIVE_DAILY_REVERSALS = "RULE_EXCESSIVE_DAILY_REVERSALS";
    public static final String REASON_AMLA_THRESHOLD = "RULE_AMLA_MANDATORY_THRESHOLD";

    private final LimitPolicy limitPolicy;
    private final VelocityCounter velocityCounter;

    /**
     * Evaluates transaction risk according to the frozen contracts-v1 scoring model:
     * S = (0.35 * S_amount) + (0.35 * S_velocity) + (0.30 * S_tier) + S_beneficiary_boost + S_reversal_penalty
     */
    public RiskEvaluationResponse evaluate(RiskEvaluationRequest request) {
        long startTime = System.currentTimeMillis();
        List<String> reasons = new ArrayList<>();

        // 1. Resolve Daily Limit & Fetch Redis Velocity State
        BigDecimal dailyLimit = request.getDailyLimit() != null
                ? request.getDailyLimit()
                : limitPolicy.getTierDailyLimit(request.getKycTier());

        VelocityCounter.VelocityState velocityState = velocityCounter.getVelocityState(request.getCustomerId());
        if (!velocityState.isRedisAvailable()) {
            reasons.add(REASON_REDIS_UNAVAILABLE);
        }

        // Use request velocity override if specified in test/contract fixture, otherwise Redis counter
        int velocityCount = request.getTransfersInLast10Min() != null
                ? request.getTransfersInLast10Min()
                : velocityState.getVelocity10Min();

        BigDecimal usedToday = velocityState.getUsedToday();

        // 2. Daily Limit Policy Check
        boolean withinLimit = limitPolicy.isWithinDailyLimit(request.getAmount(), usedToday, dailyLimit);
        String limitOutcome = withinLimit ? OUTCOME_PASSED : OUTCOME_FAILED;
        if (!withinLimit) {
            reasons.add(REASON_LIMIT_EXCEEDED);
            reasons.add(REASON_TIER_DAILY_CEILING_REACHED);
        }

        // 3. Composite Fraud Scoring
        // 3.1 S_amount: ratio of amount to daily limit (capped at 1.00)
        double sAmount = 0.0;
        if (dailyLimit.compareTo(BigDecimal.ZERO) > 0) {
            sAmount = Math.min(1.0, request.getAmount().divide(dailyLimit, 4, RoundingMode.HALF_UP).doubleValue());
        }
        if (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("500000.00")) >= 0) {
            sAmount = 1.00;
            reasons.add(REASON_AMLA_THRESHOLD);
        }

        // 3.2 S_velocity: 0 transfers=0.00, 1=0.25, 2=0.50, 3=0.75, >=4=1.00
        double sVelocity;
        if (velocityCount <= 0) {
            sVelocity = 0.00;
        } else if (velocityCount == 1) {
            sVelocity = 0.25;
        } else if (velocityCount == 2) {
            sVelocity = 0.50;
        } else if (velocityCount == 3) {
            sVelocity = 0.75;
        } else {
            sVelocity = 1.00;
            reasons.add(REASON_BURST_VELOCITY);
        }

        if (velocityCount >= 2 && velocityCount < 4) {
            reasons.add(REASON_VELOCITY_ELEVATED);
        }

        // 3.3 S_tier: TIER_3=0.10, TIER_2=0.50, TIER_1=1.00
        double sTier = resolveTierScore(request.getKycTier());

        // 3.4 S_beneficiary_boost: +0.15 if new beneficiary
        double sBeneficiary = Boolean.TRUE.equals(request.getIsNewBeneficiary()) ? 0.15 : 0.00;
        if (Boolean.TRUE.equals(request.getIsNewBeneficiary())) {
            reasons.add(REASON_NEW_BENEFICIARY);
        }

        // 3.5 S_reversal_penalty: +0.25 penalty if reversalsToday >= 2
        int reversalsToday = request.getReversalsToday() != null
                ? request.getReversalsToday()
                : velocityState.getReversalsToday();

        double sReversal = (reversalsToday >= 2) ? 0.25 : 0.00;
        if (sReversal > 0) {
            reasons.add(REASON_EXCESSIVE_DAILY_REVERSALS);
        }

        // Composite calculation
        double compositeScore = (0.35 * sAmount) + (0.35 * sVelocity) + (0.30 * sTier) + sBeneficiary + sReversal;
        compositeScore = Math.min(1.00, Math.max(0.00, compositeScore));

        // Format score to 2 decimal places
        String formattedScore = String.format(Locale.US, "%.2f", compositeScore);

        // 4. Decision Gate Evaluation
        String decision;
        String fraudOutcome;

        if (!withinLimit) {
            // Hard block on limit breach
            decision = DECISION_DECLINE;
            fraudOutcome = OUTCOME_FAILED;
        } else if (compositeScore < 0.60) {
            // Clean approval band (< 0.60)
            decision = DECISION_APPROVE;
            fraudOutcome = OUTCOME_PASSED;
            boolean redisDown = reasons.contains(REASON_REDIS_UNAVAILABLE);
            boolean amlaFlag = reasons.contains(REASON_AMLA_THRESHOLD);
            reasons.clear();
            if (redisDown) {
                reasons.add(REASON_REDIS_UNAVAILABLE);
            }
            if (amlaFlag) {
                reasons.add(REASON_AMLA_THRESHOLD);
            }
        } else if (compositeScore <= 0.85) {
            // Elevated risk band (0.60 - 0.85) -> DECLINE with REVIEW_REQUIRED
            decision = DECISION_DECLINE;
            fraudOutcome = OUTCOME_FAILED;
            if (!reasons.contains(REASON_REVIEW_REQUIRED)) {
                reasons.add(0, REASON_REVIEW_REQUIRED);
            }
        } else {
            // Outright high risk (> 0.85) -> DECLINE with HIGH_RISK
            decision = DECISION_DECLINE;
            fraudOutcome = OUTCOME_FAILED;
            if (!reasons.contains(REASON_HIGH_RISK)) {
                reasons.add(0, REASON_HIGH_RISK);
            }
        }

        // 5. If Approved, update Redis counters
        if (DECISION_APPROVE.equals(decision)) {
            velocityCounter.recordApprovedTransfer(request.getCustomerId(), request.getAmount());
        }

        long durationMs = System.currentTimeMillis() - startTime;

        FraudCheckResult fraudCheck = FraudCheckResult.builder()
                .outcome(fraudOutcome)
                .reasons(reasons)
                .build();

        LimitCheckResult limitCheck = LimitCheckResult.builder()
                .outcome(limitOutcome)
                .usedToday(usedToday.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .dailyLimit(dailyLimit.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .build();

        return RiskEvaluationResponse.builder()
                .evaluationId("eval-" + UUID.randomUUID().toString().substring(0, 8))
                .transactionId(request.getTransactionId())
                .decision(decision)
                .score(formattedScore)
                .riskScore(compositeScore)
                .fraudCheck(fraudCheck)
                .limitCheck(limitCheck)
                .reasons(reasons)
                .evaluatedAt(Instant.now())
                .evaluatedInMs(Math.max(1, durationMs))
                .executionDurationMs(Math.max(1, durationMs))
                .build();
    }

    private double resolveTierScore(String kycTier) {
        if (kycTier == null) {
            return 0.50; // default TIER_2
        }
        String normalized = kycTier.toUpperCase().trim();
        if (normalized.contains("3") || normalized.contains("FULL")) {
            return 0.10;
        } else if (normalized.contains("1") || normalized.contains("BASIC")) {
            return 1.00;
        } else {
            return 0.50;
        }
    }
}
