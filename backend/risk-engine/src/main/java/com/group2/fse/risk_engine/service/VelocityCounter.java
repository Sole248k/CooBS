package com.group2.fse.risk_engine.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.group2.fse.risk_engine.exception.RiskUnavailableException;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * The engine's counters in Redis: transfers in the last ten minutes, and what each customer
 * has used of the daily limit.
 *
 * The daily limit is kept as a reservation, the way a bank holds an authorisation against a
 * limit. An approval reserves the amount; if the transfer then does not post, the saga says
 * so and the amount is given back ({@link #release}). A day is the bank's business date in
 * Manila, and money is counted in whole ten-thousandths of a peso, never as a floating-point
 * number.
 */
@Slf4j
@Service
public class VelocityCounter {

    private static final String VELOCITY_PREFIX = "risk:velocity:";
    private static final String DAILY_RESERVED_PREFIX = "risk:daily:reserved:";
    private static final String DAILY_COUNT_PREFIX = "risk:daily:count:";
    private static final String DAILY_REVERSALS_PREFIX = "risk:daily:reversals:";
    /** One per approved transaction: "<business date>|<units>|<customerId>", what release gives back. */
    private static final String COUNTED_PREFIX = "risk:counted:";
    private static final Duration VELOCITY_WINDOW_TTL = Duration.ofMinutes(10);
    private static final Duration DAILY_METRIC_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Manila");
    /** Amounts have at most four decimals, so one unit is a ten-thousandth of a peso. */
    private static final int UNIT_SCALE = 4;

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

            String reservedVal = redisTemplate.opsForValue().get(DAILY_RESERVED_PREFIX + customerId + ":" + businessDate(Instant.now()));
            BigDecimal usedToday = reservedVal != null ? fromUnits(Long.parseLong(reservedVal)) : BigDecimal.ZERO;

            // Written by ledger-service, which dates it in UTC.
            String todayStr = LocalDate.now().format(DATE_FORMATTER);
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
     * True when this transaction already holds a reservation. A saga that was interrupted asks
     * about the same transfer again; its own amount is then already in today's total.
     */
    public boolean holdsReservation(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return false;
        }
        try {
            return requireRedis().opsForValue().get(COUNTED_PREFIX + transactionId) != null;
        } catch (RiskUnavailableException e) {
            throw e;
        } catch (Exception ex) {
            throw new RiskUnavailableException("The daily-limit reservation cannot be read.");
        }
    }

    /**
     * Reserves the amount against today's limit, and counts the transfer for the ten-minute
     * velocity rule. The amount is added first and the new total is then compared with the
     * limit, so two transfers at the same moment cannot both take the last of the room: the
     * one that goes over takes its amount back and is refused.
     *
     * A transaction is reserved once, however often the saga asks about it.
     *
     * @return false when the amount does not fit under the limit any more
     * @throws RiskUnavailableException when Redis cannot be written; nothing is approved on a guess
     */
    public boolean reserve(Long customerId, String transactionId, BigDecimal amount, BigDecimal dailyLimit) {
        try {
            StringRedisTemplate redis = requireRedis();
            String day = businessDate(Instant.now());
            long units = toUnits(amount);
            String reservedKey = DAILY_RESERVED_PREFIX + customerId + ":" + day;
            boolean tracked = transactionId != null && !transactionId.isBlank();

            if (tracked) {
                Boolean firstTime = redis.opsForValue().setIfAbsent(
                        COUNTED_PREFIX + transactionId, day + "|" + units + "|" + customerId, DAILY_METRIC_TTL);
                if (!Boolean.TRUE.equals(firstTime)) {
                    log.debug("Transaction {} already holds its reservation for customerId {}", transactionId, customerId);
                    return true;
                }
            }

            Long total = redis.opsForValue().increment(reservedKey, units);
            redis.expire(reservedKey, DAILY_METRIC_TTL);
            if (total == null || total > toUnits(dailyLimit)) {
                redis.opsForValue().increment(reservedKey, -units);
                if (tracked) {
                    redis.delete(COUNTED_PREFIX + transactionId);
                }
                log.info("Transfer {} of customerId {} no longer fits under the daily limit; not reserved",
                        transactionId, customerId);
                return false;
            }

            String velocityKey = VELOCITY_PREFIX + customerId;
            Long currentCount = redis.opsForValue().increment(velocityKey);
            if (currentCount != null && currentCount == 1) {
                redis.expire(velocityKey, VELOCITY_WINDOW_TTL);
            }
            String dailyCountKey = DAILY_COUNT_PREFIX + customerId + ":" + day;
            Long dailyCount = redis.opsForValue().increment(dailyCountKey);
            if (dailyCount != null && dailyCount == 1) {
                redis.expire(dailyCountKey, DAILY_METRIC_TTL);
            }

            log.debug("Reserved {} for customerId {}: velocity={}, reservedToday={}",
                    amount, customerId, currentCount, fromUnits(total));
            return true;
        } catch (RiskUnavailableException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Failed to reserve the daily limit for customerId {}: {}", customerId, ex.getMessage());
            throw new RiskUnavailableException("The daily-limit counter cannot be updated.");
        }
    }

    /**
     * Gives back what an approval reserved, because the transfer did not post. Safe to repeat:
     * the reservation is taken out in one step, so a second call finds nothing to give back.
     * The velocity count is left alone; an attempt is an attempt.
     *
     * @return the amount given back, or empty when the transaction holds no reservation
     * @throws RiskUnavailableException when Redis cannot be reached
     */
    public Optional<BigDecimal> release(String transactionId) {
        try {
            StringRedisTemplate redis = requireRedis();
            String held = redis.opsForValue().getAndDelete(COUNTED_PREFIX + transactionId);
            if (held == null) {
                return Optional.empty();
            }
            String[] parts = held.split("\\|");
            if (parts.length != 3) {
                // Counted before reservations recorded their amount; there is nothing exact to give back.
                return Optional.empty();
            }
            long units = Long.parseLong(parts[1]);
            String reservedKey = DAILY_RESERVED_PREFIX + parts[2] + ":" + parts[0];
            String dailyCountKey = DAILY_COUNT_PREFIX + parts[2] + ":" + parts[0];
            // A day that has already expired has nothing left to correct.
            if (Boolean.TRUE.equals(redis.hasKey(reservedKey))) {
                redis.opsForValue().increment(reservedKey, -units);
            }
            if (Boolean.TRUE.equals(redis.hasKey(dailyCountKey))) {
                redis.opsForValue().decrement(dailyCountKey);
            }
            log.info("Released the reservation of transaction {}: {} given back to customerId {}",
                    transactionId, fromUnits(units), parts[2]);
            return Optional.of(fromUnits(units));
        } catch (RiskUnavailableException e) {
            throw e;
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Failed to release the reservation of transaction {}: {}", transactionId, ex.getMessage());
            throw new RiskUnavailableException("The daily-limit counter cannot be updated.");
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

    /** The bank's business date: a day's limit starts at midnight in Manila, not in UTC. */
    static String businessDate(Instant now) {
        return LocalDate.ofInstant(now, BUSINESS_ZONE).format(DATE_FORMATTER);
    }

    static long toUnits(BigDecimal amount) {
        return amount.setScale(UNIT_SCALE, RoundingMode.HALF_UP).movePointRight(UNIT_SCALE).longValueExact();
    }

    static BigDecimal fromUnits(long units) {
        return BigDecimal.valueOf(units, UNIT_SCALE);
    }

    private StringRedisTemplate requireRedis() {
        if (redisTemplate == null) {
            throw new RiskUnavailableException("The daily-limit counter is not configured.");
        }
        return redisTemplate;
    }
}
