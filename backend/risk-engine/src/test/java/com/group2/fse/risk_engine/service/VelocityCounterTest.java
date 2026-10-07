package com.group2.fse.risk_engine.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VelocityCounterTest {

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
    @DisplayName("an approved transfer is added to the daily amount in one atomic step")
    void firstApprovalIsCounted() {
        when(values.setIfAbsent(eq("risk:counted:tx-1"), anyString(), any(Duration.class))).thenReturn(true);
        when(values.increment(anyString())).thenReturn(1L);
        when(values.increment(anyString(), anyDouble())).thenReturn(2500.0);

        counter.recordApprovedTransfer(1L, "tx-1", new BigDecimal("2500.00"));

        verify(values).increment("risk:velocity:1");
        verify(values).increment(startsWith("risk:daily:amount:1:"), eq(2500.0));
        verify(values).increment(startsWith("risk:daily:count:1:"));
        // No read-then-write of the daily amount, which could lose an update between two transfers.
        verify(values, never()).set(startsWith("risk:daily:amount:"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("a transfer the saga asks about again is not counted a second time")
    void secondEvaluationOfTheSameTransferIsNotCounted() {
        when(values.setIfAbsent(eq("risk:counted:tx-1"), anyString(), any(Duration.class))).thenReturn(false);

        counter.recordApprovedTransfer(1L, "tx-1", new BigDecimal("2500.00"));

        verify(values, never()).increment(anyString());
        verify(values, never()).increment(anyString(), anyDouble());
    }

    @Test
    @DisplayName("when Redis cannot be reached the state says so, so that the evaluator refuses to decide")
    void redisDownIsReportedNotHidden() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertFalse(counter.getVelocityState(1L).isRedisAvailable());
    }
}
