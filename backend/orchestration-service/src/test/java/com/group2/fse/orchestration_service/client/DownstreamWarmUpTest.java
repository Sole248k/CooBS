package com.group2.fse.orchestration_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DownstreamWarmUpTest {

    private final CustomerClient customers = mock(CustomerClient.class);
    private final RiskClient risk = mock(RiskClient.class);

    private DownstreamWarmUp warmUp(int maxAttempts) {
        return new DownstreamWarmUp(customers, risk, true, maxAttempts, Duration.ZERO);
    }

    @Test
    void warmUpRequestCannotBeApprovedOrRecorded() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException("CUSTOMER_NOT_FOUND", "404", null));

        warmUp(1).round();

        ArgumentCaptor<RiskClient.Request> request = ArgumentCaptor.forClass(RiskClient.Request.class);
        verify(risk).evaluate(request.capture());
        // A daily limit of zero is over the limit for any amount: the engine declines and counts nothing.
        assertThat(request.getValue().dailyLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(request.getValue().customerId()).isZero();
    }

    @Test
    void aRefusalCountsAsAnAnswer_soWarmUpStopsAfterTheExtraRounds() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException("CUSTOMER_NOT_FOUND", "404", null));

        warmUp(10).warmUp();

        // One successful round, then three more.
        verify(risk, times(4)).evaluate(any());
        verify(customers, times(4)).riskProfile(0L);
    }

    @Test
    void silenceIsNotAnAnswer_soWarmUpKeepsTryingAndThenGivesUp() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException(null, "timed out", null));
        when(risk.evaluate(any())).thenThrow(new DownstreamException(null, "timed out", null));

        warmUp(3).warmUp();

        verify(risk, times(3)).evaluate(any());
    }

    @Test
    void bothServicesMustAnswer() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException("CUSTOMER_NOT_FOUND", "404", null));
        when(risk.evaluate(any())).thenThrow(new DownstreamException(null, "timed out", null));

        assertThat(warmUp(1).round()).isFalse();
    }
}
