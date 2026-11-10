package com.group2.fse.risk_engine.service;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.group2.fse.risk_engine.dto.RiskEvaluationRequest;
import com.group2.fse.risk_engine.dto.RiskEvaluationResponse;

@ExtendWith(MockitoExtension.class)
class RiskEvaluatorTest {

    private LimitPolicy limitPolicy;

    @Mock
    private VelocityCounter velocityCounter;

    private RiskEvaluator riskEvaluator;

    @BeforeEach
    void setUp() {
        limitPolicy = new LimitPolicy();
        riskEvaluator = new RiskEvaluator(limitPolicy, velocityCounter);
        // The reservation succeeds unless a test says otherwise.
        lenient().when(velocityCounter.reserve(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Should APPROVE low-risk transfer when score < 0.60 (Clean contract example)")
    void testApproveLowRiskTransfer() {
        // Given: TIER_3 customer transferring 2500 PHP with 0 velocity
        when(velocityCounter.getVelocityState(1L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(new BigDecimal("5000.00"))
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .customerId(1L)
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_3_FULL")
                .dailyLimit(new BigDecimal("100000.00"))
                .isNewBeneficiary(false)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then
        assertNotNull(response);
        assertEquals("APPROVE", response.getDecision());
        assertEquals("PASSED", response.getFraudCheck().getOutcome());
        assertEquals("PASSED", response.getLimitCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().isEmpty(), "Approved transfer reasons must be empty");
        assertTrue(Double.parseDouble(response.getScore()) < 0.60);

        verify(velocityCounter).reserve(eq(1L), any(), eq(new BigDecimal("2500.00")), eq(new BigDecimal("100000.00")));
    }

    @Test
    @DisplayName("Should DECLINE with REVIEW_REQUIRED when score is between 0.60 and 0.85")
    void testDeclineMiddleBandReviewRequired() {
        // Given: TIER_2 (sTier=0.50 -> 0.15), amount 25,000 / 50,000 (sAmount=0.50 -> 0.175), velocity=2 (sVelocity=0.50 -> 0.175), newBeneficiary=+0.15 => Score = 0.65
        when(velocityCounter.getVelocityState(2L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(2)
                        .usedToday(BigDecimal.ZERO)
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-review-001")
                .customerId(2L)
                .amount(new BigDecimal("25000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .isNewBeneficiary(true)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then
        assertEquals("DECLINE", response.getDecision());
        assertEquals("FAILED", response.getFraudCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().contains("REVIEW_REQUIRED"));
        double score = Double.parseDouble(response.getScore());
        assertTrue(score >= 0.60 && score <= 0.85, "Score should fall into 0.60 - 0.85 band");

        // Must not record transfer in Redis on decline
        verify(velocityCounter, never()).reserve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should DECLINE with HIGH_RISK when score > 0.85 (e.g. TIER_1 basic + burst velocity + max amount)")
    void testDeclineHighRiskScore() {
        // Given: TIER_1 (sTier=1.00 -> 0.30), velocity=4 (sVelocity=1.00 -> 0.35), amount=5000/5000 (sAmount=1.00 -> 0.35) => Score = 1.00
        when(velocityCounter.getVelocityState(3L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(4)
                        .usedToday(BigDecimal.ZERO)
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-highrisk-001")
                .customerId(3L)
                .amount(new BigDecimal("5000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_1_BASIC")
                .dailyLimit(new BigDecimal("5000.00"))
                .isNewBeneficiary(false)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then
        assertEquals("DECLINE", response.getDecision());
        assertEquals("FAILED", response.getFraudCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().contains("HIGH_RISK"));
        assertTrue(response.getFraudCheck().getReasons().contains("BURST_VELOCITY_LIMIT_EXCEEDED"));
        assertTrue(Double.parseDouble(response.getScore()) > 0.85);
    }

    @Test
    @DisplayName("Should DECLINE immediately when daily limit is exceeded")
    void testDeclineDailyLimitExceeded() {
        // Given: Daily limit 50,000, already used 45,000, attempting 10,000 (Total 55,000 > 50,000)
        when(velocityCounter.getVelocityState(4L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(new BigDecimal("45000.00"))
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-limit-001")
                .customerId(4L)
                .amount(new BigDecimal("10000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .isNewBeneficiary(false)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then
        assertEquals("DECLINE", response.getDecision());
        assertEquals("FAILED", response.getLimitCheck().getOutcome());
        // Score 0.22 is in the clean band: the limit failed, the fraud check did not.
        assertEquals("PASSED", response.getFraudCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().contains("LIMIT_EXCEEDED"));
        assertTrue(response.getFraudCheck().getReasons().contains("TIER_DAILY_CEILING_REACHED"));
    }

    @Test
    @DisplayName("Should reserve nothing when the daily limit is already exceeded")
    void testLimitExceededReservesNothing() {
        when(velocityCounter.getVelocityState(4L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(new BigDecimal("45000.00"))
                        .redisAvailable(true)
                        .build()
        );

        riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-limit-002").customerId(4L).amount(new BigDecimal("10000.00"))
                .kycTier("TIER_2_STANDARD").dailyLimit(new BigDecimal("50000.00")).isNewBeneficiary(false).build());

        verify(velocityCounter, never()).reserve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Two transfers at once: the one whose reservation no longer fits is declined on the limit, not on fraud")
    void testLostRaceForTheLimitIsALimitDecline() {
        // The read says there is room; by the time the amount is reserved, another transfer took it.
        when(velocityCounter.getVelocityState(4L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(new BigDecimal("30000.00"))
                        .redisAvailable(true)
                        .build()
        );
        when(velocityCounter.reserve(any(), any(), any(), any())).thenReturn(false);

        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-race-001").customerId(4L).amount(new BigDecimal("15000.00"))
                .kycTier("TIER_3_FULL").dailyLimit(new BigDecimal("50000.00")).isNewBeneficiary(false).build());

        assertEquals("DECLINE", response.getDecision());
        assertEquals("FAILED", response.getLimitCheck().getOutcome());
        assertEquals("PASSED", response.getFraudCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().contains("LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("A transfer asked about again keeps its reservation and is not measured against its own amount")
    void testReEvaluationOfAReservedTransferIsNotDeclinedByItsOwnAmount() {
        // 95,000 used today already includes this transfer's own 10,000.
        when(velocityCounter.getVelocityState(1L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(1)
                        .usedToday(new BigDecimal("95000.00"))
                        .redisAvailable(true)
                        .build()
        );
        when(velocityCounter.holdsReservation("tx-again-001")).thenReturn(true);

        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-again-001").customerId(1L).amount(new BigDecimal("10000.00"))
                .kycTier("TIER_3_FULL").dailyLimit(new BigDecimal("100000.00")).isNewBeneficiary(false).build());

        assertEquals("APPROVE", response.getDecision());
        assertEquals("PASSED", response.getLimitCheck().getOutcome());
    }

    @Test
    @DisplayName("R2: a failed outcome gives the reservation back and says how much, as a money string")
    void testFailedOutcomeReleasesTheReservation() {
        when(velocityCounter.release("tx-out-001")).thenReturn(java.util.Optional.of(new BigDecimal("2500.0000")));

        com.group2.fse.risk_engine.dto.RiskOutcomeResponse response = riskEvaluator.recordOutcome(
                com.group2.fse.risk_engine.dto.RiskOutcomeRequest.builder()
                        .transactionId("tx-out-001").outcome("FAILED").build());

        assertTrue(response.isReleased());
        assertEquals("2500.00", response.getReleasedAmount());
        assertEquals("FAILED", response.getOutcome());
    }

    @Test
    @DisplayName("R2: a transfer with no reservation is answered released=false, with no amount")
    void testOutcomeForATransferWithNoReservation() {
        when(velocityCounter.release("tx-out-002")).thenReturn(java.util.Optional.empty());

        com.group2.fse.risk_engine.dto.RiskOutcomeResponse response = riskEvaluator.recordOutcome(
                com.group2.fse.risk_engine.dto.RiskOutcomeRequest.builder()
                        .transactionId("tx-out-002").outcome("FAILED").build());

        assertFalse(response.isReleased());
        assertEquals(null, response.getReleasedAmount());
    }

    @Test
    @DisplayName("R2: a reversed outcome counts the reversal for the customer and gives nothing back")
    void testReversedOutcomeCountsTheReversal() {
        when(velocityCounter.recordReversal(1L, "tx-out-003")).thenReturn(2);

        com.group2.fse.risk_engine.dto.RiskOutcomeResponse response = riskEvaluator.recordOutcome(
                com.group2.fse.risk_engine.dto.RiskOutcomeRequest.builder()
                        .transactionId("tx-out-003").outcome("REVERSED").customerId(1L).build());

        assertEquals("REVERSED", response.getOutcome());
        assertEquals(2, response.getReversalsToday());
        assertFalse(response.isReleased());
        assertEquals(null, response.getReleasedAmount());
        verify(velocityCounter, never()).release(anyString());
    }

    @Test
    @DisplayName("Should refuse to decide when Redis is down instead of approving with the limits switched off")
    void testRedisDownHandling() {
        // Given: Redis is unreachable
        when(velocityCounter.getVelocityState(5L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(BigDecimal.ZERO)
                        .redisAvailable(false)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-redis-down-001")
                .customerId(5L)
                .amount(new BigDecimal("25000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .isNewBeneficiary(true)
                .build();

        // When / Then
        org.junit.jupiter.api.Assertions.assertThrows(
                com.group2.fse.risk_engine.exception.RiskUnavailableException.class,
                () -> riskEvaluator.evaluate(request));
        verify(velocityCounter, never()).reserve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should apply +0.25 penalty and flag RULE_EXCESSIVE_DAILY_REVERSALS when reversalsToday >= 2")
    void testReversalAbusePenalty() {
        // Given: TIER_2 (sTier=0.50 -> 0.15), amount 10,000 / 50,000 (sAmount=0.20 -> 0.07), velocity 0 (0.00)
        // Normal base score = 0.07 + 0.00 + 0.15 = 0.22 (APPROVE)
        // With reversalsToday = 2: penalty +0.25 => Score = 0.22 + 0.25 = 0.47
        // If combined with new beneficiary (+0.15): Score = 0.47 + 0.15 = 0.62 => DECLINE (REVIEW_REQUIRED)
        when(velocityCounter.getVelocityState(6L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(2)
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-rev-penalty-001")
                .customerId(6L)
                .amount(new BigDecimal("10000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .isNewBeneficiary(true)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then
        assertEquals("DECLINE", response.getDecision());
        assertEquals("FAILED", response.getFraudCheck().getOutcome());
        assertTrue(response.getFraudCheck().getReasons().contains("RULE_EXCESSIVE_DAILY_REVERSALS"));
        assertTrue(response.getFraudCheck().getReasons().contains("REVIEW_REQUIRED"));
        double score = Double.parseDouble(response.getScore());
        assertEquals(0.62, score, 0.01);
    }

    @Test
    @DisplayName("Should flag RULE_AMLA_MANDATORY_THRESHOLD when amount >= 500,000")
    void testAmlaMandatoryThreshold() {
        // Given: TIER_3 customer transferring 500,000 PHP
        when(velocityCounter.getVelocityState(7L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build()
        );

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("tx-amla-001")
                .customerId(7L)
                .amount(new BigDecimal("500000.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_3_FULL")
                .dailyLimit(new BigDecimal("500000.00"))
                .isNewBeneficiary(false)
                .build();

        // When
        RiskEvaluationResponse response = riskEvaluator.evaluate(request);

        // Then
        assertTrue(response.getFraudCheck().getReasons().contains("RULE_AMLA_MANDATORY_THRESHOLD"));
    }

    // --- the customer's own risk score is a floor under the transfer's score ---

    /** A small, slow transfer by a full-KYC member: on its own it scores about 0.03. */
    private RiskEvaluationResponse smallTransferBy(Long customerId, String customerRiskScore) {
        when(velocityCounter.getVelocityState(customerId)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());

        return riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-floor-" + customerRiskScore)
                .customerId(customerId)
                .amount(new BigDecimal("100.00"))
                .currency("PHP")
                .channel("APP")
                .customerRiskScore(customerRiskScore == null ? null : new BigDecimal(customerRiskScore))
                .kycTier("TIER_3_FULL")
                .dailyLimit(new BigDecimal("100000.00"))
                .isNewBeneficiary(false)
                .build());
    }

    @Test
    @DisplayName("A member rated 0.72 is declined REVIEW_REQUIRED even for a small transfer, and nothing is reserved")
    void testCustomerRatedInTheReviewBandIsDeclined() {
        RiskEvaluationResponse response = smallTransferBy(6L, "0.72");

        assertEquals("DECLINE", response.getDecision());
        assertEquals("0.72", response.getScore());
        assertEquals("FAILED", response.getFraudCheck().getOutcome());
        assertEquals("REVIEW_REQUIRED", response.getFraudCheck().getReasons().get(0));
        assertTrue(response.getFraudCheck().getReasons().contains("CUSTOMER_RISK_RATING"));
        // It is a fraud decline, not a limit one.
        assertEquals("PASSED", response.getLimitCheck().getOutcome());
        verify(velocityCounter, never()).reserve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A member rated 0.92 is declined HIGH_RISK")
    void testCustomerRatedHighRiskIsDeclined() {
        RiskEvaluationResponse response = smallTransferBy(5L, "0.92");

        assertEquals("DECLINE", response.getDecision());
        assertEquals("0.92", response.getScore());
        assertEquals("HIGH_RISK", response.getFraudCheck().getReasons().get(0));
        assertTrue(response.getFraudCheck().getReasons().contains("CUSTOMER_RISK_RATING"));
    }

    @Test
    @DisplayName("The floor uses the same bands as the score: 0.59 passes, 0.60 and 0.85 are review, 0.86 is high risk")
    void testFloorBandEdges() {
        assertEquals("APPROVE", smallTransferBy(1L, "0.59").getDecision());
        assertEquals("REVIEW_REQUIRED", smallTransferBy(1L, "0.60").getFraudCheck().getReasons().get(0));
        assertEquals("REVIEW_REQUIRED", smallTransferBy(1L, "0.85").getFraudCheck().getReasons().get(0));
        assertEquals("HIGH_RISK", smallTransferBy(1L, "0.86").getFraudCheck().getReasons().get(0));
    }

    @Test
    @DisplayName("A low-rated member is approved as before; the score is never below the member's own")
    void testLowRatedCustomerIsUnaffected() {
        RiskEvaluationResponse response = smallTransferBy(1L, "0.15");

        assertEquals("APPROVE", response.getDecision());
        assertEquals("0.15", response.getScore());
        assertTrue(response.getFraudCheck().getReasons().isEmpty());
    }

    @Test
    @DisplayName("No customer score, or one below the transfer's own score, changes nothing")
    void testFloorDoesNotLowerOrInventAScore() {
        assertEquals("0.03", smallTransferBy(1L, null).getScore());
        assertEquals("0.03", smallTransferBy(1L, "0.01").getScore());
    }

    @Test
    @DisplayName("A transfer that scores above its member's rating is declined for its own reasons, not the rating")
    void testTransferRiskierThanItsCustomerKeepsItsOwnScore() {
        // TIER_1 (0.30) with four transfers in ten minutes (0.35): 0.65, above the member's 0.22.
        when(velocityCounter.getVelocityState(9L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(4)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());

        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-floor-burst")
                .customerId(9L)
                .amount(new BigDecimal("10.00"))
                .currency("PHP")
                .channel("APP")
                .customerRiskScore(new BigDecimal("0.22"))
                .kycTier("TIER_1_BASIC")
                .dailyLimit(new BigDecimal("5000.00"))
                .isNewBeneficiary(false)
                .build());

        assertEquals("DECLINE", response.getDecision());
        assertTrue(Double.parseDouble(response.getScore()) >= 0.65);
        assertFalse(response.getFraudCheck().getReasons().contains("CUSTOMER_RISK_RATING"));
    }

    @Test
    @DisplayName("Impossible travel anomaly is flagged and penalizes composite score by +0.35")
    void testImpossibleTravelAnomalyFlagged() {
        when(velocityCounter.getVelocityState(1L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());
        when(velocityCounter.checkGeoVelocity(eq(1L), any(), any(), any())).thenReturn(
                VelocityCounter.GeoVelocityResult.builder()
                        .impossibleTravel(true)
                        .distanceKm(9500.0)
                        .speedKmh(15000.0)
                        .build());

        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-geo-anomaly")
                .customerId(1L)
                .amount(new BigDecimal("1000.00"))
                .currency("PHP")
                .kycTier("TIER_3_ENHANCED")
                .dailyLimit(new BigDecimal("500000.00"))
                .latitude(44.4268)
                .longitude(26.1025)
                .build());

        assertEquals("DECLINE", response.getDecision());
        assertTrue(response.getFraudCheck().getReasons().contains("IMPOSSIBLE_TRAVEL_ANOMALY"));
        assertTrue(response.getFraudCheck().getReasons().contains("REVIEW_REQUIRED"));
        assertTrue(Double.parseDouble(response.getScore()) >= 0.75);
    }

    @Test
    @DisplayName("Unrecognized hardware device is flagged and adds +0.20 score penalty")
    void testUnrecognizedDeviceFlagged() {
        when(velocityCounter.getVelocityState(2L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(2)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());
        when(velocityCounter.isDeviceRecognized(2L, "unknown-device-hash")).thenReturn(false);

        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-device-ato")
                .customerId(2L)
                .amount(new BigDecimal("25000.00"))
                .currency("PHP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .deviceFingerprint("unknown-device-hash")
                .build());

        assertEquals("DECLINE", response.getDecision());
        assertTrue(response.getFraudCheck().getReasons().contains("UNRECOGNIZED_DEVICE_HARDWARE"));
        // Baseline 0.50 + 0.20 device penalty = 0.70
        assertEquals(0.70, Double.parseDouble(response.getScore()), 0.02);
    }

    @Test
    @DisplayName("Dormant account sudden large transfer (>90 days) triggers DORMANT_ACCOUNT_SUDDEN_ACTIVITY (+0.30)")
    void testDormantAccountSuddenActivityFlagged() {
        when(velocityCounter.getVelocityState(3L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(1)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());

        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-dormant-mule")
                .customerId(3L)
                .amount(new BigDecimal("25000.00"))
                .currency("PHP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .daysSinceLastActivity(120)
                .isDormantAccount(true)
                .build());

        assertEquals("DECLINE", response.getDecision());
        assertTrue(response.getFraudCheck().getReasons().contains("DORMANT_ACCOUNT_SUDDEN_ACTIVITY"));
        // Baseline 0.395 + 0.30 dormant penalty = ~0.70
        assertEquals(0.70, Double.parseDouble(response.getScore()), 0.02);
    }

    @Test
    @DisplayName("AMLA Structuring / Smurfing Evasion triggers RULE_AMLA_STRUCTURING_SUSPECTED and sets Score=1.00")
    void testAmlaStructuringEvasionFlagged() {
        when(velocityCounter.getVelocityState(4L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(1)
                        .usedToday(new BigDecimal("95000.00"))
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());

        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-amla-smurf")
                .customerId(4L)
                .amount(new BigDecimal("450000.00")) // Near 500k ceiling with prior spend
                .currency("PHP")
                .kycTier("TIER_3_ENHANCED")
                .dailyLimit(new BigDecimal("1000000.00"))
                .isStructuringSuspected(true)
                .build());

        assertEquals("DECLINE", response.getDecision());
        assertEquals("1.00", response.getScore());
        assertTrue(response.getFraudCheck().getReasons().contains("RULE_AMLA_STRUCTURING_SUSPECTED"));
    }

    @Test
    @DisplayName("Personalized behavioral baseline: transfer exceeding 3-sigma outlier triggers BEHAVIORAL_AMOUNT_ANOMALY")
    void testPersonalizedBehavioralAmountAnomalyFlagged() {
        when(velocityCounter.getVelocityState(5L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(1)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());
        when(velocityCounter.getCustomerSpendStats(5L)).thenReturn(
                VelocityCounter.CustomerSpendStats.builder()
                        .mean(new BigDecimal("1500.00"))
                        .stdDev(new BigDecimal("500.00"))
                        .txCount(15)
                        .build());

        // Baseline spend is 1,500. Sending 45,000 gives Z = (45,000 - 1,500) / 500 = 87.0 (>> 3.0 sigma)
        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-behavioral-spike")
                .customerId(5L)
                .amount(new BigDecimal("45000.00"))
                .currency("PHP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .build());

        assertEquals("DECLINE", response.getDecision());
        assertTrue(response.getFraudCheck().getReasons().contains("BEHAVIORAL_AMOUNT_ANOMALY"));
        assertTrue(response.getFraudCheck().getReasons().contains("REVIEW_REQUIRED"));
        // Baseline 0.55 + 0.25 behavioral penalty = 0.80
        assertEquals(0.80, Double.parseDouble(response.getScore()), 0.02);
    }

    @Test
    @DisplayName("Personalized behavioral baseline: normal transfer within 3-sigma does not trigger BEHAVIORAL_AMOUNT_ANOMALY")
    void testPersonalizedBehavioralNormalSpendNotFlagged() {
        when(velocityCounter.getVelocityState(6L)).thenReturn(
                VelocityCounter.VelocityState.builder()
                        .velocity10Min(0)
                        .usedToday(BigDecimal.ZERO)
                        .reversalsToday(0)
                        .redisAvailable(true)
                        .build());
        when(velocityCounter.getCustomerSpendStats(6L)).thenReturn(
                VelocityCounter.CustomerSpendStats.builder()
                        .mean(new BigDecimal("1500.00"))
                        .stdDev(new BigDecimal("500.00"))
                        .txCount(15)
                        .build());
        when(velocityCounter.reserve(eq(6L), eq("tx-normal-spend"), any(), any())).thenReturn(true);

        // Spending 2,000 gives Z = (2,000 - 1,500) / 500 = 1.0 (< 3.0 sigma)
        RiskEvaluationResponse response = riskEvaluator.evaluate(RiskEvaluationRequest.builder()
                .transactionId("tx-normal-spend")
                .customerId(6L)
                .amount(new BigDecimal("2000.00"))
                .currency("PHP")
                .kycTier("TIER_2_STANDARD")
                .dailyLimit(new BigDecimal("50000.00"))
                .build());

        assertEquals("APPROVE", response.getDecision());
        assertFalse(response.getFraudCheck().getReasons().contains("BEHAVIORAL_AMOUNT_ANOMALY"));
        verify(velocityCounter).updateCustomerSpendStats(eq(6L), eq(new BigDecimal("2000.00")));
    }
}
