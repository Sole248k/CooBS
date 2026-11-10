package com.group2.fse.risk_engine.service;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitPolicyTest {

    private LimitPolicy limitPolicy;

    @BeforeEach
    void setUp() {
        limitPolicy = new LimitPolicy();
    }

    @Test
    @DisplayName("Should resolve correct KYC tier ceilings")
    void testGetTierDailyLimit() {
        assertEquals(new BigDecimal("5000.00"), limitPolicy.getTierDailyLimit("TIER_1_BASIC"));
        assertEquals(new BigDecimal("50000.00"), limitPolicy.getTierDailyLimit("TIER_2_STANDARD"));
        assertEquals(new BigDecimal("500000.00"), limitPolicy.getTierDailyLimit("TIER_3_FULL"));
        // Default fallback to standard
        assertEquals(new BigDecimal("50000.00"), limitPolicy.getTierDailyLimit(null));
        assertEquals(new BigDecimal("50000.00"), limitPolicy.getTierDailyLimit("UNKNOWN_TIER"));
    }

    @Test
    @DisplayName("Should approve when amount + usedToday is within daily limit")
    void testWithinDailyLimit() {
        BigDecimal amount = new BigDecimal("2500.00");
        BigDecimal usedToday = new BigDecimal("1000.00");
        BigDecimal dailyLimit = new BigDecimal("5000.00");

        assertTrue(limitPolicy.isWithinDailyLimit(amount, usedToday, dailyLimit));
    }

    @Test
    @DisplayName("Should approve when amount + usedToday exactly equals daily limit")
    void testExactlyAtDailyLimit() {
        BigDecimal amount = new BigDecimal("4000.00");
        BigDecimal usedToday = new BigDecimal("1000.00");
        BigDecimal dailyLimit = new BigDecimal("5000.00");

        assertTrue(limitPolicy.isWithinDailyLimit(amount, usedToday, dailyLimit));
    }

    @Test
    @DisplayName("Should decline when amount + usedToday exceeds daily limit")
    void testExceedsDailyLimit() {
        BigDecimal amount = new BigDecimal("4001.00");
        BigDecimal usedToday = new BigDecimal("1000.00");
        BigDecimal dailyLimit = new BigDecimal("5000.00");

        assertFalse(limitPolicy.isWithinDailyLimit(amount, usedToday, dailyLimit));
    }
}
