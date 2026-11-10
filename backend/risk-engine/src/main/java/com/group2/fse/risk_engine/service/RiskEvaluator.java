package com.group2.fse.risk_engine.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.group2.fse.risk_engine.dto.FraudCheckResult;
import com.group2.fse.risk_engine.dto.LimitCheckResult;
import com.group2.fse.risk_engine.dto.RiskEvaluationRequest;
import com.group2.fse.risk_engine.dto.RiskEvaluationResponse;
import com.group2.fse.risk_engine.dto.RiskOutcomeRequest;
import com.group2.fse.risk_engine.dto.RiskOutcomeResponse;
import com.group2.fse.risk_engine.exception.RiskUnavailableException;

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
    public static final String REASON_CUSTOMER_RISK_RATING = "CUSTOMER_RISK_RATING";
    public static final String REASON_IMPOSSIBLE_TRAVEL = "IMPOSSIBLE_TRAVEL_ANOMALY";
    public static final String REASON_UNRECOGNIZED_DEVICE = "UNRECOGNIZED_DEVICE_HARDWARE";
    public static final String REASON_DORMANT_ACCOUNT_SUDDEN_ACTIVITY = "DORMANT_ACCOUNT_SUDDEN_ACTIVITY";
    public static final String REASON_AMLA_STRUCTURING = "RULE_AMLA_STRUCTURING_SUSPECTED";
    public static final String REASON_BEHAVIORAL_AMOUNT_ANOMALY = "BEHAVIORAL_AMOUNT_ANOMALY";

    private final LimitPolicy limitPolicy;
    private final VelocityCounter velocityCounter;

    /**
     * Evaluates transaction risk according to the frozen contracts-v1 scoring model:
     * S = (0.35 * S_amount) + (0.35 * S_velocity) + (0.30 * S_tier) + S_beneficiary_boost + S_reversal_penalty
     * and the customer's own risk score is a floor under it: the final score is the higher of the two.
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
            // Without the counters neither the daily limit nor the velocity can be checked.
            // Approving on a guess of "nothing used today" would switch the limits off.
            throw new RiskUnavailableException("The velocity and daily-limit counters cannot be read.");
        }

        // Use request velocity override if specified in test/contract fixture, otherwise Redis counter
        int velocityCount = request.getTransfersInLast10Min() != null
                ? request.getTransfersInLast10Min()
                : velocityState.getVelocity10Min();

        BigDecimal usedToday = velocityState.getUsedToday();

        // 2. Daily Limit Policy Check
        // A saga that was interrupted asks again about a transfer it already holds a reservation
        // for. Its own amount is then in today's total, and must not be counted against it twice.
        boolean alreadyReserved = velocityCounter.holdsReservation(request.getTransactionId());
        boolean withinLimit = alreadyReserved
                || limitPolicy.isWithinDailyLimit(request.getAmount(), usedToday, dailyLimit);
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

        // 3.6 S_device: +0.20 penalty if unrecognized hardware
        double sDevice = 0.00;
        if (Boolean.TRUE.equals(request.getIsNewDevice())
                || (request.getDeviceFingerprint() != null && !velocityCounter.isDeviceRecognized(request.getCustomerId(), request.getDeviceFingerprint()))) {
            sDevice = 0.20;
            reasons.add(REASON_UNRECOGNIZED_DEVICE);
        }

        // 3.7 S_geo: +0.35 penalty if impossible travel anomaly
        double sGeo = 0.00;
        if (request.getLatitude() != null && request.getLongitude() != null) {
            VelocityCounter.GeoVelocityResult geoResult = velocityCounter.checkGeoVelocity(
                    request.getCustomerId(), request.getLatitude(), request.getLongitude(), Instant.now());
            if (geoResult.isImpossibleTravel()) {
                sGeo = 0.35;
                reasons.add(REASON_IMPOSSIBLE_TRAVEL);
            }
        }

        // 3.8 S_dormant: +0.30 penalty if dormant account sudden drain
        double sDormant = 0.00;
        boolean isDormant = Boolean.TRUE.equals(request.getIsDormantAccount())
                || (request.getDaysSinceLastActivity() != null && request.getDaysSinceLastActivity() >= 90);
        if (isDormant && request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("5000.00")) >= 0) {
            sDormant = 0.30;
            reasons.add(REASON_DORMANT_ACCOUNT_SUDDEN_ACTIVITY);
        }

        // 3.9 AMLA Structuring / Smurfing Evasion Check
        boolean isStructuring = Boolean.TRUE.equals(request.getIsStructuringSuspected());
        if (!isStructuring && request.getAmount() != null) {
            boolean nearAmlaCeiling = request.getAmount().compareTo(new BigDecimal("400000.00")) >= 0
                    && request.getAmount().compareTo(new BigDecimal("500000.00")) < 0;
            boolean cumulativeBreach = usedToday.add(request.getAmount()).compareTo(new BigDecimal("500000.00")) >= 0
                    && usedToday.compareTo(BigDecimal.ZERO) > 0;
            if ((nearAmlaCeiling && velocityCount >= 1) || (cumulativeBreach && nearAmlaCeiling)) {
                isStructuring = true;
            }
        }
        if (isStructuring) {
            sAmount = 1.00;
            reasons.add(REASON_AMLA_STRUCTURING);
        }

        // 3.10 S_behavioral: Personalized Z-Score Anomaly Detection
        double sBehavioral = 0.00;
        BigDecimal avgAmount = request.getAvgTransactionAmount();
        BigDecimal stdDevAmount = request.getStdDevTransactionAmount();
        if (avgAmount == null && request.getCustomerId() != null) {
            VelocityCounter.CustomerSpendStats stats = velocityCounter.getCustomerSpendStats(request.getCustomerId());
            if (stats != null && stats.getMean() != null && stats.getMean().compareTo(BigDecimal.ZERO) > 0) {
                avgAmount = stats.getMean();
                stdDevAmount = stats.getStdDev();
            }
        }
        if (avgAmount != null && avgAmount.compareTo(BigDecimal.ZERO) > 0 && request.getAmount() != null) {
            double mu = avgAmount.doubleValue();
            double sigma = (stdDevAmount != null && stdDevAmount.compareTo(BigDecimal.ZERO) > 0)
                    ? stdDevAmount.doubleValue()
                    : (mu * 0.40); // 40% default coefficient of variation
            double x = request.getAmount().doubleValue();
            if (x > mu) {
                double zScore = (x - mu) / sigma;
                if (zScore >= 3.0) { // 3 sigma deviation (99.87th percentile outlier)
                    sBehavioral = 0.25;
                    reasons.add(REASON_BEHAVIORAL_AMOUNT_ANOMALY);
                }
            }
        }

        // Composite calculation
        double compositeScore = (0.35 * sAmount) + (0.35 * sVelocity) + (0.30 * sTier) 
                + sBeneficiary + sReversal + sDevice + sGeo + sDormant + sBehavioral;
        if (sGeo > 0.0) {
            compositeScore = Math.max(compositeScore, 0.75);
        }
        if (isStructuring) {
            compositeScore = 1.00;
        }
        compositeScore = Math.min(1.00, Math.max(0.00, compositeScore));

        // 3.6 The customer's own risk rating is a floor. A transfer is never judged safer than
        // the member who sends it, so a member rated in a decline band is declined whatever
        // the amount. The reason says so, for staff reading the timeline.
        double customerRisk = request.getCustomerRiskScore() != null
                ? Math.min(1.00, Math.max(0.00, request.getCustomerRiskScore().doubleValue()))
                : 0.00;
        if (customerRisk > compositeScore) {
            compositeScore = customerRisk;
            if (customerRisk >= 0.60) {
                reasons.add(REASON_CUSTOMER_RISK_RATING);
            }
        }

        // Format score to 2 decimal places
        String formattedScore = String.format(Locale.US, "%.2f", compositeScore);

        // 4. Decision Gate Evaluation
        // The fraud outcome follows the score alone and the limit outcome follows the limit
        // alone, so a transfer over its daily limit is reported as a limit failure and not as fraud.
        String fraudOutcome;
        if (compositeScore < 0.60) {
            // Clean band (< 0.60)
            fraudOutcome = OUTCOME_PASSED;
        } else if (compositeScore <= 0.85) {
            // Elevated risk band (0.60 - 0.85) -> REVIEW_REQUIRED
            fraudOutcome = OUTCOME_FAILED;
            if (!reasons.contains(REASON_REVIEW_REQUIRED)) {
                reasons.add(0, REASON_REVIEW_REQUIRED);
            }
        } else {
            // Outright high risk (> 0.85) -> HIGH_RISK
            fraudOutcome = OUTCOME_FAILED;
            if (!reasons.contains(REASON_HIGH_RISK)) {
                reasons.add(0, REASON_HIGH_RISK);
            }
        }

        String decision = withinLimit && OUTCOME_PASSED.equals(fraudOutcome) ? DECISION_APPROVE : DECISION_DECLINE;

        // 5. An approval reserves the amount against today's limit. The reservation is the real
        // limit check: the read above can be stale when two transfers arrive together, and the
        // one that no longer fits is refused here.
        if (DECISION_APPROVE.equals(decision)
                && !velocityCounter.reserve(request.getCustomerId(), request.getTransactionId(), request.getAmount(), dailyLimit)) {
            limitOutcome = OUTCOME_FAILED;
            reasons.add(REASON_LIMIT_EXCEEDED);
            reasons.add(REASON_TIER_DAILY_CEILING_REACHED);
            decision = DECISION_DECLINE;
        }

        if (DECISION_APPROVE.equals(decision)) {
            if (request.getDeviceFingerprint() != null) {
                velocityCounter.registerDevice(request.getCustomerId(), request.getDeviceFingerprint());
            }
            if (request.getAmount() != null && request.getCustomerId() != null) {
                velocityCounter.updateCustomerSpendStats(request.getCustomerId(), request.getAmount());
            }
            // An approval carries no rule reasons, only the AMLA flag when it applies.
            boolean amlaFlag = reasons.contains(REASON_AMLA_THRESHOLD);
            reasons.clear();
            if (amlaFlag) {
                reasons.add(REASON_AMLA_THRESHOLD);
            }
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

    /**
     * R2. The saga reports what became of a transfer. FAILED: it was approved here but did not
     * post, and the amount it reserved goes back to the customer's daily limit. REVERSED: it
     * posted and was reversed afterwards, which counts as one of the customer's reversals for
     * the day and gives nothing back. Repeating a report changes nothing.
     */
    public RiskOutcomeResponse recordOutcome(RiskOutcomeRequest request) {
        if (RiskOutcomeRequest.REVERSED.equals(request.getOutcome())) {
            int reversalsToday = velocityCounter.recordReversal(request.getCustomerId(), request.getTransactionId());
            return RiskOutcomeResponse.builder()
                    .transactionId(request.getTransactionId())
                    .outcome(request.getOutcome())
                    .released(false)
                    .reversalsToday(reversalsToday)
                    .build();
        }
        Optional<BigDecimal> released = velocityCounter.release(request.getTransactionId());
        return RiskOutcomeResponse.builder()
                .transactionId(request.getTransactionId())
                .outcome(request.getOutcome())
                .released(released.isPresent())
                .releasedAmount(released.map(RiskEvaluator::money).orElse(null))
                .build();
    }

    /** Money in the contract is a string with two to four decimals. */
    private static String money(BigDecimal amount) {
        BigDecimal exact = amount.stripTrailingZeros();
        return (exact.scale() < 2 ? exact.setScale(2, RoundingMode.UNNECESSARY) : exact).toPlainString();
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
