package com.group2.fse.transaction_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.client.dto.StartTransferSagaRequest;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@DisplayName("New beneficiary: what transaction-service tells the saga, and so the risk engine")
class NewBeneficiaryFlagTest {

    private static final Instant CREATED = Instant.parse("2026-10-08T03:00:00Z");

    private static TxnTransaction transfer() {
        return TxnTransaction.builder()
                .transactionId(UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"))
                .referenceNo("TX-20261008-8F3A21C0")
                .channel("APP")
                .customerId(1L)
                .sourceAccountId(1L)
                .destinationAccountId(2L)
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .status("PROCESSING")
                .createdAt(CREATED)
                .build();
    }

    // The repository's own rule runs for real; only the query behind it is answered here.
    private static TxnTransactionRepository repositoryWhere(boolean anEarlierTransferPosted) {
        TxnTransactionRepository repository = mock(TxnTransactionRepository.class, Mockito.CALLS_REAL_METHODS);
        doReturn(anEarlierTransferPosted).when(repository)
                .existsByCustomerIdAndDestinationAccountIdAndStatusAndCreatedAtBefore(1L, 2L, "POSTED", CREATED);
        return repository;
    }

    @Test
    @DisplayName("The first transfer to an account is to a new beneficiary")
    void noEarlierPostedTransferMeansNew() {
        assertThat(repositoryWhere(false).isFirstPostedTransferTo(transfer())).isTrue();
    }

    @Test
    @DisplayName("Once a transfer to that account has posted, the beneficiary is no longer new")
    void anEarlierPostedTransferMeansKnown() {
        assertThat(repositoryWhere(true).isFirstPostedTransferTo(transfer())).isFalse();
    }

    @Test
    @DisplayName("The flag travels in O1 under the contract's name")
    void theFlagIsSentAsIsNewBeneficiary() throws Exception {
        StartTransferSagaRequest request = TransferServiceImpl.sagaRequest(transfer(), 1L, "CUSTOMER", true);

        String json = new ObjectMapper().writeValueAsString(request);

        // Not "newBeneficiary": the orchestrator reads the name in contract section 8.
        assertThat(json).contains("\"isNewBeneficiary\":true").doesNotContain("\"newBeneficiary\"");
    }
}
