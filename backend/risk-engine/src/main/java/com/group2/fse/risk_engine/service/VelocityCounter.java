package com.group2.fse.risk_engine.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class VelocityCounter {

    private static final String VELOCITY_PREFIX = "risk:velocity:";
    private static final String DAILY_AMOUNT_PREFIX = "risk:daily:amount:";
    private static final String DAILY_COUNT_PREFIX = "risk:daily:count:";
    private static final Duration VELOCITY_WINDOW_TTL = Duration.ofMinutes(10);
    private static final Duration DAILY_METRIC_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StringRedisTemplate redisTemplate;

    public VelocityCounter() {
        this.redisTemplate = null;
    }

    @Autowired(required = false)
    public VelocityCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Data
    @Builder
    public static class VelocityState {
        private final int velocity10Min;
        private final BigDecimal usedToday;
        private final boolean redisAvailable;
    }

    /**
     * Reads current velocity and daily accumulated amount for a customer.
     * Safely handles Redis outages by falling back and setting redisAvailable = false.
     */
    public VelocityState getVelocityState(Long customerId) {
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate bean not configured. Running in degraded mode.");
            return VelocityState.builder()
                    .velocity10Min(0)
                    .usedToday(BigDecimal.ZERO)
                    .redisAvailable(false)
                    .build();
        }

        try {
            String velocityKey = VELOCITY_PREFIX + customerId;
            String velocityVal = redisTemplate.opsForValue().get(velocityKey);
            int velocityCount = velocityVal != null ? Integer.parseInt(velocityVal) : 0;

            String todayStr = LocalDate.now().format(DATE_FORMATTER);
            String dailyAmountKey = DAILY_AMOUNT_PREFIX + customerId + ":" + todayStr;
            String dailyAmountVal = redisTemplate.opsForValue().get(dailyAmountKey);
            BigDecimal usedToday = dailyAmountVal != null ? new BigDecimal(dailyAmountVal) : BigDecimal.ZERO;

            return VelocityState.builder()
                    .velocity10Min(velocityCount)
                    .usedToday(usedToday)
                    .redisAvailable(true)
                    .build();
        } catch (Exception ex) {
            log.error("Redis unreachable while querying velocity for customerId {}: {}", customerId, ex.getMessage());
            return VelocityState.builder()
                    .velocity10Min(0)
                    .usedToday(BigDecimal.ZERO)
                    .redisAvailable(false)
                    .build();
        }
    }

    /**
     * Records an approved transfer in Redis: increments 10-minute burst counter and daily spent amount.
     */
    public void recordApprovedTransfer(Long customerId, BigDecimal amount) {
        if (redisTemplate == null || amount == null) {
            return;
        }

        try {
            String velocityKey = VELOCITY_PREFIX + customerId;
            Long currentCount = redisTemplate.opsForValue().increment(velocityKey);
            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(velocityKey, VELOCITY_WINDOW_TTL);
            }

            String todayStr = LocalDate.now().format(DATE_FORMATTER);
            String dailyAmountKey = DAILY_AMOUNT_PREFIX + customerId + ":" + todayStr;
            String dailyCountKey = DAILY_COUNT_PREFIX + customerId + ":" + todayStr;

            // Increment daily amount by float/double or string addition
            String existingDailyVal = redisTemplate.opsForValue().get(dailyAmountKey);
            BigDecimal existingDaily = existingDailyVal != null ? new BigDecimal(existingDailyVal) : BigDecimal.ZERO;
            BigDecimal newDaily = existingDaily.add(amount);
            redisTemplate.opsForValue().set(dailyAmountKey, newDaily.toPlainString(), DAILY_METRIC_TTL);

            Long dailyCount = redisTemplate.opsForValue().increment(dailyCountKey);
            if (dailyCount != null && dailyCount == 1) {
                redisTemplate.expire(dailyCountKey, DAILY_METRIC_TTL);
            }

            log.debug("Updated Redis metrics for customerId {}: velocity={}, dailyTotal={}",
                    customerId, currentCount, newDaily);
        } catch (Exception ex) {
            log.error("Failed to update Redis velocity for customerId {}: {}", customerId, ex.getMessage());
        }
    }
}
