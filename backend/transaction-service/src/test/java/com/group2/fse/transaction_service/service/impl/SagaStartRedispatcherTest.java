package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.StartTransferSagaRequest;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaStartRedispatcher Unit Tests")
class SagaStartRedispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");

    @Mock
    private TxnTransactionRepository transactionRepository;

    @Mock
    private OrchestrationClient orchestrationClient;

    private SagaStartRedispatcher redispatcher() {
        return new SagaStartRedispatcher(transactionRepository, orchestrationClient,
                Duration.ofSeconds(30), Duration.ofMinutes(15));
    }

    private static TxnTransaction waiting(Long destinationAccountId) {
        return TxnTransaction.builder()
                .transactionId(UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"))
                .referenceNo("TX-20261008-8F3A21C0")
                .channel("APP")
                .customerId(1L)
                .sourceAccountId(1L)
                .destinationAccountId(destinationAccountId)
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.0000"))
                .currency("PHP")
                .status("PROCESSING")
                .createdAt(NOW.minusSeconds(45))
                .build();
    }

    @Test
    @DisplayName("sends O1 again for a transfer that is still PROCESSING with no saga stage, inside the retry window")
    void resendsTheSagaStartForATransferWithNoSagaStage() {
        when(transactionRepository.findProcessingWithoutSagaStage(NOW.minus(Duration.ofMinutes(15)), NOW.minusSeconds(30)))
                .thenReturn(List.of(waiting(2L)));

        redispatcher().redispatch(NOW);

        ArgumentCaptor<StartTransferSagaRequest> saga = ArgumentCaptor.forClass(StartTransferSagaRequest.class);
        verify(orchestrationClient).triggerTransferSaga(saga.capture(), anyString());
        assertThat(saga.getValue().getTransactionId()).isEqualTo(UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"));
        assertThat(saga.getValue().getReferenceNo()).isEqualTo("TX-20261008-8F3A21C0");
        assertThat(saga.getValue().getCustomerId()).isEqualTo(1L);
        assertThat(saga.getValue().getDestinationAccountId()).isEqualTo(2L);
        assertThat(saga.getValue().getAmount()).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("a start that is sent again says whether the beneficiary is new, as the first one did")
    void theRepeatedStartCarriesTheNewBeneficiaryFlag() {
        TxnTransaction txn = waiting(2L);
        when(transactionRepository.findProcessingWithoutSagaStage(any(), any())).thenReturn(List.of(txn));
        when(transactionRepository.isFirstPostedTransferTo(txn)).thenReturn(true);

        redispatcher().redispatch(NOW);

        ArgumentCaptor<StartTransferSagaRequest> saga = ArgumentCaptor.forClass(StartTransferSagaRequest.class);
        verify(orchestrationClient).triggerTransferSaga(saga.capture(), anyString());
        assertThat(saga.getValue().getIsNewBeneficiary()).isTrue();
    }

    @Test
    @DisplayName("does not send a start for a row without a resolved destination: there is nothing safe to send")
    void skipsARowWithoutAResolvedDestination() {
        when(transactionRepository.findProcessingWithoutSagaStage(any(), any())).thenReturn(List.of(waiting(null)));

        redispatcher().redispatch(NOW);

        verify(orchestrationClient, never()).triggerTransferSaga(any(), any());
    }
}
