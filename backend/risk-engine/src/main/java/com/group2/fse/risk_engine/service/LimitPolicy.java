package com.group2.fse.risk_engine.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class LimitPolicy {

    public static final BigDecimal TIER_1_BASIC_LIMIT = new BigDecimal("5000.00");
    public static final BigDecimal TIER_2_STANDARD_LIMIT = new BigDecimal("50000.00");
    public static final BigDecimal TIER_3_FULL_LIMIT = new BigDecimal("500000.00");

    private static final Map<String, BigDecimal> TIER_LIMITS;

    static {
        Map<String, BigDecimal> limits = new HashMap<>();
        limits.put("TIER_1_BASIC", TIER_1_BASIC_LIMIT);
        limits.put("TIER_1", TIER_1_BASIC_LIMIT);
        limits.put("BASIC", TIER_1_BASIC_LIMIT);

        limits.put("TIER_2_STANDARD", TIER_2_STANDARD_LIMIT);
        limits.put("TIER_2", TIER_2_STANDARD_LIMIT);
        limits.put("STANDARD", TIER_2_STANDARD_LIMIT);

        limits.put("TIER_3_FULL", TIER_3_FULL_LIMIT);
        limits.put("TIER_3", TIER_3_FULL_LIMIT);
        limits.put("FULL", TIER_3_FULL_LIMIT);
        TIER_LIMITS = Collections.unmodifiableMap(limits);
    }

    /**
     * Resolves the maximum daily transfer ceiling for a member's KYC tier.
     */
    public BigDecimal getTierDailyLimit(String kycTier) {
        if (kycTier == null) {
            return TIER_2_STANDARD_LIMIT;
        }
        return TIER_LIMITS.getOrDefault(kycTier.toUpperCase().trim(), TIER_2_STANDARD_LIMIT);
    }

    /**
     * Validates whether requested transfer amount + used today exceeds daily limit.
     * Returns true if within limit, false if exceeded.
     */
    public boolean isWithinDailyLimit(BigDecimal amount, BigDecimal usedToday, BigDecimal dailyLimit) {
        if (amount == null) {
            return false;
        }
        BigDecimal safeUsedToday = usedToday != null ? usedToday : BigDecimal.ZERO;
        BigDecimal safeDailyLimit = dailyLimit != null ? dailyLimit : TIER_2_STANDARD_LIMIT;

        BigDecimal totalProjected = safeUsedToday.add(amount);
        return totalProjected.compareTo(safeDailyLimit) <= 0;
    }
}
