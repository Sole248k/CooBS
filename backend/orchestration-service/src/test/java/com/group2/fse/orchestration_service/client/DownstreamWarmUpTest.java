package com.group2.fse.orchestration_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void warmUpRequestIsOneTheEngineApproves_forACustomerWhoDoesNotExist() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException("CUSTOMER_NOT_FOUND", "404", null));

        warmUp(1).round();

        ArgumentCaptor<RiskClient.Request> request = ArgumentCaptor.forClass(RiskClient.Request.class);
        verify(risk).evaluate(request.capture());
        // Only an approval makes the engine write its counters, and that write is what must be warm.
        assertThat(request.getValue().amount()).isLessThan(request.getValue().dailyLimit());
        assertThat(request.getValue().customerRiskScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(request.getValue().isNewBeneficiary()).isFalse();
        assertThat(request.getValue().customerId()).isZero();
    }

    @Test
    void everyRoundCarriesANewTransactionId_soTheEngineCountsItAndWritesAgain() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException("CUSTOMER_NOT_FOUND", "404", null));
        DownstreamWarmUp warmUp = warmUp(1);

        warmUp.round();
        warmUp.round();

        ArgumentCaptor<RiskClient.Request> requests = ArgumentCaptor.forClass(RiskClient.Request.class);
        verify(risk, times(2)).evaluate(requests.capture());
        assertThat(requests.getAllValues().get(0).transactionId())
                .isNotEqualTo(requests.getAllValues().get(1).transactionId());
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

    @Test
    void keepWarmSendsOneRoundEachTime_onceTheStartUpWarmUpHasFinished() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException("CUSTOMER_NOT_FOUND", "404", null));
        DownstreamWarmUp warmUp = warmUp(10);
        warmUp.warmUp();

        warmUp.keepWarm();
        warmUp.keepWarm();

        // Four rounds at start-up, then one for each interval.
        verify(risk, times(6)).evaluate(any());
        verify(customers, times(6)).riskProfile(0L);
    }

    @Test
    void keepWarmWaitsForTheStartUpWarmUp_soTheTwoDoNotRunTogether() {
        warmUp(10).keepWarm();

        verify(risk, never()).evaluate(any());
        verify(customers, never()).riskProfile(0L);
    }

    @Test
    void keepWarmStillRuns_whenTheStartUpWarmUpGaveUp() {
        when(customers.riskProfile(0L)).thenThrow(new DownstreamException(null, "timed out", null));
        when(risk.evaluate(any())).thenThrow(new DownstreamException(null, "timed out", null));
        DownstreamWarmUp warmUp = warmUp(2);
        warmUp.warmUp();

        // A round that gets no answer is not an error; the next interval sends another.
        warmUp.keepWarm();
        warmUp.keepWarm();

        verify(risk, times(4)).evaluate(any());
    }

    @Test
    void aSwitchedOffWarmUpSendsNothing() {
        DownstreamWarmUp off = new DownstreamWarmUp(customers, risk, false, 1, Duration.ZERO);
        off.warmUp();

        off.keepWarm();

        // Only the four rounds the test ran by calling warmUp() itself.
        verify(risk, times(4)).evaluate(any());
    }
}
