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
    private static final String DAILY_REVERSALS_PREFIX = "risk:daily:reversals:";
    private static final String COUNTED_PREFIX = "risk:counted:";
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
        private final int reversalsToday;
        private final boolean redisAvailable;
    }

    /**
     * Reads current velocity, daily accumulated amount, and daily reversal count for a customer.
     * Safely handles Redis outages by falling back and setting redisAvailable = false.
     */
    public VelocityState getVelocityState(Long customerId) {
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate bean not configured. Running in degraded mode.");
            return VelocityState.builder()
                    .velocity10Min(0)
                    .usedToday(BigDecimal.ZERO)
                    .reversalsToday(0)
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

            String dailyReversalsKey = DAILY_REVERSALS_PREFIX + customerId + ":" + todayStr;
            String dailyReversalsVal = redisTemplate.opsForValue().get(dailyReversalsKey);
            int reversalsToday = dailyReversalsVal != null ? Integer.parseInt(dailyReversalsVal) : 0;

            return VelocityState.builder()
                    .velocity10Min(velocityCount)
                    .usedToday(usedToday)
                    .reversalsToday(reversalsToday)
                    .redisAvailable(true)
                    .build();
        } catch (Exception ex) {
            log.error("Redis unreachable while querying velocity for customerId {}: {}", customerId, ex.getMessage());
            return VelocityState.builder()
                    .velocity10Min(0)
                    .usedToday(BigDecimal.ZERO)
                    .reversalsToday(0)
                    .redisAvailable(false)
                    .build();
        }
    }

    /**
     * Records an approved transfer in Redis: increments the 10-minute burst counter and the daily
     * spent amount. A transfer is counted once, however often the saga asks about it again: a
     * saga that is resumed evaluates the risk a second time for the same transaction.
     */
    public void recordApprovedTransfer(Long customerId, String transactionId, BigDecimal amount) {
        if (redisTemplate == null || amount == null) {
            return;
        }

        try {
            if (transactionId != null && !transactionId.isBlank()) {
                Boolean firstTime = redisTemplate.opsForValue()
                        .setIfAbsent(COUNTED_PREFIX + transactionId, "1", DAILY_METRIC_TTL);
                if (!Boolean.TRUE.equals(firstTime)) {
                    log.debug("Transaction {} was already counted for customerId {}", transactionId, customerId);
                    return;
                }
            }

            String velocityKey = VELOCITY_PREFIX + customerId;
            Long currentCount = redisTemplate.opsForValue().increment(velocityKey);
            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(velocityKey, VELOCITY_WINDOW_TTL);
            }

            String todayStr = LocalDate.now().format(DATE_FORMATTER);
            String dailyAmountKey = DAILY_AMOUNT_PREFIX + customerId + ":" + todayStr;
            String dailyCountKey = DAILY_COUNT_PREFIX + customerId + ":" + todayStr;

            // INCRBYFLOAT adds in one step, so two transfers at the same moment cannot lose an update.
            Double newDaily = redisTemplate.opsForValue().increment(dailyAmountKey, amount.doubleValue());
            redisTemplate.expire(dailyAmountKey, DAILY_METRIC_TTL);

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

    /**
     * Records a committed reversal in Redis: increments daily reversal counter.
     */
    public void recordReversal(Long customerId) {
        if (redisTemplate == null || customerId == null) {
            return;
        }

        try {
            String todayStr = LocalDate.now().format(DATE_FORMATTER);
            String dailyReversalsKey = DAILY_REVERSALS_PREFIX + customerId + ":" + todayStr;
            Long currentCount = redisTemplate.opsForValue().increment(dailyReversalsKey);
            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(dailyReversalsKey, DAILY_METRIC_TTL);
            }
            log.debug("Incremented daily reversals in Redis for customerId {}: count={}", customerId, currentCount);
        } catch (Exception ex) {
            log.error("Failed to record reversal in Redis for customerId {}: {}", customerId, ex.getMessage());
        }
    }
}
