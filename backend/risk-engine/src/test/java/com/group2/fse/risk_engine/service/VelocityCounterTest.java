package com.group2.fse.risk_engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.group2.fse.risk_engine.exception.RiskUnavailableException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VelocityCounterTest {

    // 2500.00 and 100000.00 pesos in ten-thousandths.
    private static final long AMOUNT_UNITS = 25_000_000L;
    private static final BigDecimal AMOUNT = new BigDecimal("2500.00");
    private static final BigDecimal LIMIT = new BigDecimal("100000.00");
    private static final String TODAY = VelocityCounter.businessDate(Instant.now());

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> values;

    private VelocityCounter counter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        counter = new VelocityCounter(redisTemplate);
    }

    @Test
    @DisplayName("An approval reserves the amount in whole units, and counts the transfer once")
    void firstApprovalIsReserved() {
        when(values.setIfAbsent(eq("risk:counted:tx-1"), anyString(), any(Duration.class))).thenReturn(true);
        when(values.increment(startsWith("risk:daily:reserved:1:"), eq(AMOUNT_UNITS))).thenReturn(AMOUNT_UNITS);
        when(values.increment(anyString())).thenReturn(1L);

        assertTrue(counter.reserve(1L, "tx-1", AMOUNT, LIMIT));

        // The reservation remembers its day, amount and customer, so that it can be given back exactly.
        verify(values).setIfAbsent(eq("risk:counted:tx-1"), eq(TODAY + "|" + AMOUNT_UNITS + "|1"), any(Duration.class));
        verify(values).increment("risk:daily:reserved:1:" + TODAY, AMOUNT_UNITS);
        verify(values).increment("risk:velocity:1");
        verify(values).increment("risk:daily:count:1:" + TODAY);
    }

    @Test
    @DisplayName("A second evaluation of the same transfer keeps its reservation and adds nothing")
    void secondEvaluationOfTheSameTransferIsNotCountedAgain() {
        when(values.setIfAbsent(eq("risk:counted:tx-1"), anyString(), any(Duration.class))).thenReturn(false);

        assertTrue(counter.reserve(1L, "tx-1", AMOUNT, LIMIT));

        verify(values, never()).increment(anyString());
        verify(values, never()).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("Two transfers at the same moment: the one that no longer fits takes its amount back and is refused")
    void theTransferThatGoesOverTheLimitIsRefusedAndLeavesNothingBehind() {
        when(values.setIfAbsent(eq("risk:counted:tx-2"), anyString(), any(Duration.class))).thenReturn(true);
        // Another transfer was added between the read and this step: the total is now over the limit.
        when(values.increment(startsWith("risk:daily:reserved:1:"), eq(AMOUNT_UNITS))).thenReturn(1_010_000_000L);

        assertFalse(counter.reserve(1L, "tx-2", AMOUNT, LIMIT));

        verify(values).increment("risk:daily:reserved:1:" + TODAY, -AMOUNT_UNITS);
        verify(redisTemplate).delete("risk:counted:tx-2");
        // A refused transfer is not an approved one: it does not count for velocity either.
        verify(values, never()).increment("risk:velocity:1");
    }

    @Test
    @DisplayName("A transfer that uses the limit exactly is allowed")
    void exactlyTheLimitFits() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(values.increment(startsWith("risk:daily:reserved:1:"), eq(1_000_000_000L))).thenReturn(1_000_000_000L);
        when(values.increment(anyString())).thenReturn(1L);

        assertTrue(counter.reserve(1L, "tx-3", LIMIT, LIMIT));
    }

    @Test
    @DisplayName("Release gives back exactly what was reserved, on the day it was reserved")
    void releaseGivesBackTheReservation() {
        when(values.getAndDelete("risk:counted:tx-1")).thenReturn("2026-10-09|" + AMOUNT_UNITS + "|1");
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        Optional<BigDecimal> released = counter.release("tx-1");

        assertEquals(0, released.orElseThrow().compareTo(AMOUNT));
        verify(values).increment("risk:daily:reserved:1:2026-10-09", -AMOUNT_UNITS);
        verify(values).decrement("risk:daily:count:1:2026-10-09");
        // An attempt is an attempt: the ten-minute count is left alone.
        verify(values, never()).decrement("risk:velocity:1");
    }

    @Test
    @DisplayName("Release is safe to repeat, and does nothing for a transfer that was never approved")
    void releaseWithNoReservationChangesNothing() {
        when(values.getAndDelete("risk:counted:tx-9")).thenReturn(null);

        assertTrue(counter.release("tx-9").isEmpty());

        verify(values, never()).increment(anyString(), anyLong());
        verify(values, never()).decrement(anyString());
    }

    @Test
    @DisplayName("A reservation counted before amounts were recorded cannot be given back exactly, so nothing is")
    void releaseOfAnOldStyleMarkerChangesNothing() {
        when(values.getAndDelete("risk:counted:tx-old")).thenReturn("1");

        assertTrue(counter.release("tx-old").isEmpty());

        verify(values, never()).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("A reversal is counted once for the sender, on the Manila business date")
    void reversalIsCountedOnce() {
        when(values.setIfAbsent(eq("risk:reversed:tx-1"), anyString(), any(Duration.class))).thenReturn(true);
        when(values.increment("risk:daily:reversals:1:" + TODAY)).thenReturn(2L);

        assertEquals(2, counter.recordReversal(1L, "tx-1"));

        verify(values).increment("risk:daily:reversals:1:" + TODAY);
        // The amount the transfer reserved stays reserved: a reversal is not a refund of the limit.
        verify(values, never()).increment(anyString(), anyLong());
        verify(values, never()).getAndDelete(anyString());
    }

    @Test
    @DisplayName("A reversal reported twice is counted once, and the answer is still today's count")
    void repeatedReversalReportIsNotCountedAgain() {
        when(values.setIfAbsent(eq("risk:reversed:tx-1"), anyString(), any(Duration.class))).thenReturn(false);
        when(values.get("risk:daily:reversals:1:" + TODAY)).thenReturn("2");

        assertEquals(2, counter.recordReversal(1L, "tx-1"));

        verify(values, never()).increment(anyString());
    }

    @Test
    @DisplayName("Today's reversals are read from the Manila business date, the one they are written under")
    void reversalsTodayAreReadFromTheBusinessDate() {
        when(values.get("risk:daily:reversals:1:" + TODAY)).thenReturn("3");

        assertEquals(3, counter.getVelocityState(1L).getReversalsToday());
    }

    @Test
    @DisplayName("Today's total is read back as money")
    void usedTodayIsReadFromTheReservedUnits() {
        when(values.get("risk:daily:reserved:1:" + TODAY)).thenReturn("25001234");

        assertEquals(0, counter.getVelocityState(1L).getUsedToday().compareTo(new BigDecimal("2500.1234")));
    }

    @Test
    @DisplayName("The business date is the date in Manila, not in UTC")
    void businessDateIsManila() {
        // 17:30 UTC on the 9th is 01:30 on the 10th in Manila.
        assertEquals("2026-10-10", VelocityCounter.businessDate(Instant.parse("2026-10-09T17:30:00Z")));
        assertEquals("2026-10-09", VelocityCounter.businessDate(Instant.parse("2026-10-09T15:59:59Z")));
    }

    @Test
    @DisplayName("Amounts are counted exactly: no floating-point rounding")
    void amountsAreExactUnits() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(values.increment(anyString(), anyLong())).thenReturn(1L);
        when(values.increment(anyString())).thenReturn(1L);

        counter.reserve(1L, "tx-4", new BigDecimal("0.10"), LIMIT);
        counter.reserve(1L, "tx-5", new BigDecimal("1234.5678"), LIMIT);

        ArgumentCaptor<Long> units = ArgumentCaptor.forClass(Long.class);
        verify(values, org.mockito.Mockito.times(2)).increment(startsWith("risk:daily:reserved:1:"), units.capture());
        assertEquals(java.util.List.of(1_000L, 12_345_678L), units.getAllValues());
    }

    @Test
    @DisplayName("when Redis cannot be reached the state says so, so that the evaluator refuses to decide")
    void redisDownIsReportedNotHidden() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertFalse(counter.getVelocityState(1L).isRedisAvailable());
    }

    @Test
    @DisplayName("If Redis fails while reserving, nothing is approved on a guess")
    void redisDownWhileReservingIsNotAnApproval() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThrows(RiskUnavailableException.class, () -> counter.reserve(1L, "tx-6", AMOUNT, LIMIT));
        assertThrows(RiskUnavailableException.class, () -> new VelocityCounter().release("tx-6"));
        assertThrows(RiskUnavailableException.class, () -> counter.recordReversal(1L, "tx-6"));
    }
}
