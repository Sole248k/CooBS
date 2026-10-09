package com.group2.fse.transaction_service.interbank.service;

import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.client.dto.InboundInterbankSagaRequest;
import com.group2.fse.transaction_service.client.dto.InboundInterbankSagaResponse;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.interbank.dto.InterbankAccountLookupResponse;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferRequest;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferResponse;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterbankService: Inbound transfer orchestration, idempotency and validations")
class InterbankServiceTest {

    @Mock
    private TxnTransactionRepository transactionRepository;

    @Mock
    private OrchestrationClient orchestrationClient;

    private InterbankSecurityProperties properties;
    private InterbankServiceImpl interbankService;

    @BeforeEach
    void setUp() {
        properties = new InterbankSecurityProperties();
        properties.setDefaultSettlementAccountId(9005L);
        interbankService = new InterbankServiceImpl(transactionRepository, orchestrationClient, properties);
    }

    private InterbankTransferRequest sampleRequest() {
        return InterbankTransferRequest.builder()
                .messageId("0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21")
                .sourceBankCode("BANKB")
                .destinationBankCode("COOBS")
                .sender(InterbankTransferRequest.Party.builder()
                        .accountNumber("1000-2000-3000")
                        .name("Jane Smith")
                        .build())
                .beneficiary(InterbankTransferRequest.Beneficiary.builder()
                        .accountNumber("ACC_10000004")
                        .name("Juan Dela Cruz")
                        .build())
                .amount("2500.00")
                .currency("PHP")
                .purpose("Payment for services")
                .senderReference("TX-20261003-8F3A21C0")
                .createdAt("2026-10-08T11:40:00Z")
                .build();
    }

    @Test
    @DisplayName("Wrong destination bank code is rejected with WRONG_DESTINATION_BANK")
    void wrongDestinationBankRejected() {
        InterbankTransferRequest request = sampleRequest();
        request.setDestinationBankCode("OTHERBANK");

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getReasonCode()).isEqualTo("WRONG_DESTINATION_BANK");
        verifyNoInteractions(orchestrationClient);
    }

    @Test
    @DisplayName("Unsupported currency is rejected with CURRENCY_NOT_SUPPORTED")
    void unsupportedCurrencyRejected() {
        InterbankTransferRequest request = sampleRequest();
        request.setCurrency("USD");

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getReasonCode()).isEqualTo("CURRENCY_NOT_SUPPORTED");
        verifyNoInteractions(orchestrationClient);
    }

    @Test
    @DisplayName("Non-positive amount is rejected with INVALID_AMOUNT")
    void invalidAmountRejected() {
        InterbankTransferRequest request = sampleRequest();
        request.setAmount("0.00");

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getReasonCode()).isEqualTo("INVALID_AMOUNT");
        verifyNoInteractions(orchestrationClient);
    }

    @Test
    @DisplayName("Unknown beneficiary account is rejected with ACCOUNT_NOT_FOUND")
    void beneficiaryNotFoundRejected() {
        InterbankTransferRequest request = sampleRequest();
        when(orchestrationClient.findAccountByNumber("ACC_10000004")).thenReturn(Optional.empty());

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getReasonCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Inactive beneficiary account is rejected with ACCOUNT_NOT_ACTIVE")
    void inactiveBeneficiaryRejected() {
        InterbankTransferRequest request = sampleRequest();
        AccountLookupResponse frozenAccount = AccountLookupResponse.builder()
                .accountId(4L)
                .accountNumber("ACC_10000004")
                .status("FROZEN")
                .build();
        when(orchestrationClient.findAccountByNumber("ACC_10000004")).thenReturn(Optional.of(frozenAccount));

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getReasonCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valid inbound transfer successfully executes saga and returns ACCEPTED with masked name")
    void successfulInboundTransferAccepted() {
        InterbankTransferRequest request = sampleRequest();
        AccountLookupResponse account = AccountLookupResponse.builder()
                .accountId(4L)
                .customerId(10L)
                .accountNumber("ACC_10000004")
                .status("ACTIVE")
                .build();
        when(orchestrationClient.findAccountByNumber("ACC_10000004")).thenReturn(Optional.of(account));

        UUID txId = UUID.randomUUID();
        when(orchestrationClient.triggerInboundInterbankSaga(any(InboundInterbankSagaRequest.class), anyString()))
                .thenAnswer(inv -> {
                    InboundInterbankSagaRequest req = inv.getArgument(0);
                    return new InboundInterbankSagaResponse(req.getTransactionId(), "COMPLETED", req.getReferenceNo());
                });

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getMessageId()).isEqualTo(request.getMessageId());
        assertThat(response.getReceiverReference()).startsWith("IB-");
        assertThat(response.getBeneficiaryName()).isEqualTo("J*** D*** C***");
        assertThat(response.getCreditedAt()).isNotNull();

        // Committed before the saga is called, then once more with the outcome.
        verify(transactionRepository).saveAndFlush(any(TxnTransaction.class));
        verify(transactionRepository).save(any(TxnTransaction.class));
    }

    private AccountLookupResponse activeBeneficiary() {
        return AccountLookupResponse.builder()
                .accountId(4L).customerId(10L).accountNumber("ACC_10000004").status("ACTIVE").build();
    }

    @Test
    @DisplayName("No answer from the saga is not a rejection: 503, and the row stays open")
    void noAnswerFromSaga_leavesTheRowOpen() {
        InterbankTransferRequest request = sampleRequest();
        when(orchestrationClient.findAccountByNumber("ACC_10000004")).thenReturn(Optional.of(activeBeneficiary()));
        when(orchestrationClient.triggerInboundInterbankSaga(any(InboundInterbankSagaRequest.class), anyString()))
                .thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> interbankService.processInboundTransfer(request))
                .isInstanceOf(com.group2.fse.transaction_service.exception.TransferRejectedException.class)
                .hasMessageContaining("Send the same message again");

        // The ledger may have posted, so nothing records FAILED.
        verify(transactionRepository).saveAndFlush(any(TxnTransaction.class));
        verify(transactionRepository, never()).save(any(TxnTransaction.class));
    }

    @Test
    @DisplayName("A repeat of a message with no outcome asks the saga again with the same transaction and reference")
    void repeatOfOpenMessage_settlesTheSameTransaction() {
        InterbankTransferRequest request = sampleRequest();
        TxnTransaction open = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("IB-20261008-123456")
                .idempotencyKey("IB:" + request.getMessageId())
                .sourceAccountId(9005L)
                .destinationAccountId(4L)
                .amount(new java.math.BigDecimal("2500.00"))
                .status("PROCESSING")
                .createdAt(Instant.now())
                .build();
        when(transactionRepository.findByIdempotencyKey("IB:" + request.getMessageId())).thenReturn(Optional.of(open));
        org.mockito.ArgumentCaptor<InboundInterbankSagaRequest> sent =
                org.mockito.ArgumentCaptor.forClass(InboundInterbankSagaRequest.class);
        when(orchestrationClient.triggerInboundInterbankSaga(sent.capture(), anyString()))
                .thenReturn(new InboundInterbankSagaResponse(open.getTransactionId(), "COMPLETED", open.getReferenceNo()));

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getReceiverReference()).isEqualTo("IB-20261008-123456");
        assertThat(sent.getValue().getTransactionId()).isEqualTo(open.getTransactionId());
        assertThat(sent.getValue().getReferenceNo()).isEqualTo("IB-20261008-123456");
        assertThat(open.getStatus()).isEqualTo("COMPLETED");
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Two copies at once: the one that loses the unique key answers from the winner's row")
    void secondCopyArrivingTogether_doesNotPostUnderAnotherReference() {
        InterbankTransferRequest request = sampleRequest();
        TxnTransaction winner = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("IB-20261008-654321")
                .idempotencyKey("IB:" + request.getMessageId())
                .status("COMPLETED")
                .completedAt(Instant.now())
                .build();
        when(transactionRepository.findByIdempotencyKey("IB:" + request.getMessageId()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(orchestrationClient.findAccountByNumber("ACC_10000004")).thenReturn(Optional.of(activeBeneficiary()));
        when(transactionRepository.saveAndFlush(any(TxnTransaction.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq idempotency_key"));

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getReceiverReference()).isEqualTo("IB-20261008-654321");
        verify(orchestrationClient, never()).triggerInboundInterbankSaga(any(), any());
    }

    @Test
    @DisplayName("Status of a message with no outcome is 503, never ACCEPTED")
    void statusOfOpenMessage_isNotAccepted() {
        TxnTransaction open = TxnTransaction.builder()
                .transactionId(UUID.randomUUID()).referenceNo("IB-20261008-123456")
                .status("PROCESSING").createdAt(Instant.now()).build();
        when(transactionRepository.findByIdempotencyKey("IB:open-id")).thenReturn(Optional.of(open));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> interbankService.getTransferStatus("open-id"))
                .isInstanceOf(com.group2.fse.transaction_service.exception.TransferRejectedException.class);
    }

    @Test
    @DisplayName("Duplicate messageId returns cached answer without re-executing saga")
    void duplicateMessageIdReplayed() {
        InterbankTransferRequest request = sampleRequest();
        TxnTransaction existing = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("IB-20261008-123456")
                .idempotencyKey("IB:" + request.getMessageId())
                .status("COMPLETED")
                .completedAt(Instant.now())
                .build();
        when(transactionRepository.findByIdempotencyKey("IB:" + request.getMessageId())).thenReturn(Optional.of(existing));

        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getReceiverReference()).isEqualTo("IB-20261008-123456");
        verify(orchestrationClient, never()).triggerInboundInterbankSaga(any(), any());
    }

    @Test
    @DisplayName("getTransferStatus returns NOT_FOUND when messageId never seen")
    void statusNotFound() {
        when(transactionRepository.findByIdempotencyKey("IB:unknown-id")).thenReturn(Optional.empty());

        InterbankTransferResponse response = interbankService.getTransferStatus("unknown-id");

        assertThat(response.getStatus()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("checkAccount returns exists=true and masked name when active account found")
    void checkAccountFound() {
        AccountLookupResponse account = AccountLookupResponse.builder()
                .accountId(4L)
                .accountNumber("ACC_10000004")
                .status("ACTIVE")
                .currency("PHP")
                .build();
        when(orchestrationClient.findAccountByNumber("ACC_10000004")).thenReturn(Optional.of(account));

        InterbankAccountLookupResponse response = interbankService.checkAccount("ACC_10000004");

        assertThat(response.isExists()).isTrue();
        assertThat(response.getActive()).isTrue();
        assertThat(response.getMaskedName()).isNotBlank();
        assertThat(response.getCurrency()).isEqualTo("PHP");
    }
}
