package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.TimelineResponseDto;
import com.group2.fse.transaction_service.dto.TransactionDetailResponseDto;
import com.group2.fse.transaction_service.entity.TxnStageEvent;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.exception.TransactionNotFoundException;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.service.impl.TimelineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimelineServiceImpl Unit Tests (FIN-304)")
class TimelineServiceImplTest {

    @Mock
    private TxnTransactionRepository transactionRepository;

    @Mock
    private TxnStageEventRepository stageEventRepository;

    @InjectMocks
    private TimelineServiceImpl timelineService;

    private UUID transactionId;
    private TxnTransaction testTxn;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        testTxn = TxnTransaction.builder()
                .transactionId(transactionId)
                .referenceNo("TX-20261006-A1B2C3D4")
                .channel("APP")
                .sourceAccountId(1L)
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .category("TRANSFER")
                .status("PROCESSING")
                .customerId(1L)
                .createdAt(Instant.now())
                .build();
        // Unless a test says otherwise, a teller is reading.
        signInAs(16L, "teller_alice", "ROLE_TELLER");
    }

    @org.junit.jupiter.api.AfterEach
    void signOut() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private static UsernamePasswordAuthenticationToken signInAs(long userId, String username, String role) {
        com.group2.fse.transaction_service.security.UserPrincipal principal =
                com.group2.fse.transaction_service.security.UserPrincipal.create(userId, username, List.of(role));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    @Test
    @DisplayName("a customer who asks for the staff view gets the customer view")
    void testCustomerAskingForStaffViewGetsCustomerView() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));
        when(stageEventRepository.findByTransactionIdOrderByStageNoAsc(transactionId)).thenReturn(List.of());

        TimelineResponseDto response = timelineService.getTimeline(
                transactionId.toString(), "STAFF", signInAs(1L, "john_doe", "ROLE_CUSTOMER"));

        assertThat(response.getView()).isEqualTo("CUSTOMER");
        assertThat(response.getStages()).hasSize(3);
    }

    @Test
    @DisplayName("a customer cannot read another customer's timeline or transaction")
    void testCustomerCannotReadAnotherCustomersTransaction() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));
        UsernamePasswordAuthenticationToken maria = signInAs(2L, "maria_santos", "ROLE_CUSTOMER");

        assertThatThrownBy(() -> timelineService.getTimeline(transactionId.toString(), null, maria))
                .isInstanceOf(com.group2.fse.transaction_service.exception.TransferRejectedException.class)
                .hasMessageContaining("another customer");
        assertThatThrownBy(() -> timelineService.getTransactionDetail(transactionId.toString()))
                .isInstanceOf(com.group2.fse.transaction_service.exception.TransferRejectedException.class);
        assertThatThrownBy(() -> timelineService.getExecutionStatus(transactionId.toString()))
                .isInstanceOf(com.group2.fse.transaction_service.exception.TransferRejectedException.class);
    }

    @Test
    @DisplayName("a teller may ask for the customer view")
    void testTellerMayAskForCustomerView() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));
        when(stageEventRepository.findByTransactionIdOrderByStageNoAsc(transactionId)).thenReturn(List.of());

        TimelineResponseDto response = timelineService.getTimeline(transactionId.toString(), "CUSTOMER", null);

        assertThat(response.getView()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("getTimeline returns customer view by default for customer principal")
    void testGetTimelineCustomerViewDefault() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));
        when(stageEventRepository.findByTransactionIdOrderByStageNoAsc(transactionId)).thenReturn(List.of(
                TxnStageEvent.builder().stageNo(1).stageName("INITIATED").outcome("PASSED").occurredAt(Instant.now()).build(),
                TxnStageEvent.builder().stageNo(2).stageName("VALIDATED").outcome("PASSED").occurredAt(Instant.now()).build()
        ));

        UsernamePasswordAuthenticationToken auth = signInAs(1L, "john_doe", "ROLE_CUSTOMER");

        TimelineResponseDto response = timelineService.getTimeline(transactionId.toString(), null, auth);

        assertThat(response.getView()).isEqualTo("CUSTOMER");
        assertThat(response.getTotalStages()).isEqualTo(3);
        assertThat(response.getStages()).hasSize(3);
        assertThat(response.getStages().get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getStages().get(1).getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getStages().get(2).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getTimeline returns staff view when requested view is STAFF")
    void testGetTimelineStaffView() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));
        when(stageEventRepository.findByTransactionIdOrderByStageNoAsc(transactionId)).thenReturn(List.of(
                TxnStageEvent.builder().stageNo(1).stageName("INITIATED").outcome("PASSED").occurredAt(Instant.now()).build()
        ));

        TimelineResponseDto response = timelineService.getTimeline(transactionId.toString(), "STAFF", null);

        assertThat(response.getView()).isEqualTo("STAFF");
        assertThat(response.getTotalStages()).isEqualTo(11);
        assertThat(response.getStages()).hasSize(11);
        assertThat(response.getStages().get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getStages().get(0).getTitle()).isEqualTo("Initiated");
        assertThat(response.getStages()).extracting("stageName").containsExactly(
                "INITIATED", "VALIDATED", "AUTHENTICATED", "FRAUD_CHECK", "LIMIT_CHECK", "FUNDS_CHECK",
                "AUTHORIZED", "POSTED", "LEDGER_UPDATE", "NOTIFICATION", "RECONCILIATION");
        assertThat(response.getStages().get(1).getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getStages().get(2).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getTimeline resolves transaction by referenceNo if UUID parsing fails")
    void testGetTimelineResolvesByReferenceNo() {
        when(transactionRepository.findByReferenceNo("TX-20261006-A1B2C3D4")).thenReturn(Optional.of(testTxn));
        when(stageEventRepository.findByTransactionIdOrderByStageNoAsc(transactionId)).thenReturn(List.of());

        TimelineResponseDto response = timelineService.getTimeline("TX-20261006-A1B2C3D4", null, null);

        assertThat(response.getTrackingReference()).isEqualTo("TX-20261006-A1B2C3D4");
        assertThat(response.getTransactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("getTimeline throws TransactionNotFoundException when transaction does not exist")
    void testGetTimelineNotFound() {
        when(transactionRepository.findByTransactionId(any())).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceNo(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timelineService.getTimeline(UUID.randomUUID().toString(), null, null))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("getTransactionDetail returns correct DTO")
    void testGetTransactionDetail() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));

        TransactionDetailResponseDto detail = timelineService.getTransactionDetail(transactionId.toString());

        assertThat(detail.getTransactionId()).isEqualTo(transactionId);
        assertThat(detail.getReferenceNo()).isEqualTo("TX-20261006-A1B2C3D4");
        assertThat(detail.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(detail.getSourceAccountNumber()).isEqualTo("ACC_10000001");
        assertThat(detail.getDestinationAccountNumber()).isEqualTo("ACC_10000002");
    }

    @Test
    @DisplayName("recordStage saves stage event and updates transaction status and completedAt")
    void testRecordStage() {
        when(stageEventRepository.findByTransactionIdAndStageNo(transactionId, 8)).thenReturn(Optional.empty());
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));

        Instant now = Instant.now();
        timelineService.recordStage(transactionId, 8, "POSTED", "PASSED", "Journal Ref: TRF-123", now, "POSTED", null);

        verify(stageEventRepository).save(any(TxnStageEvent.class));
        verify(transactionRepository).save(testTxn);
        assertThat(testTxn.getStatus()).isEqualTo("POSTED");
        assertThat(testTxn.getCompletedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("recordStage marks the original REVERSED when its reversal posts")
    void testRecordStageReversalPostedMarksTheOriginalReversed() {
        UUID reversalId = UUID.randomUUID();
        TxnTransaction reversal = TxnTransaction.builder()
                .transactionId(reversalId)
                .referenceNo("RV-20261003-11AA22BB")
                .status("PROCESSING")
                .reversesTransactionId(transactionId)
                .build();
        testTxn.setStatus("POSTED");
        when(stageEventRepository.findByTransactionIdAndStageNo(reversalId, 8)).thenReturn(Optional.empty());
        when(transactionRepository.findByTransactionId(reversalId)).thenReturn(Optional.of(reversal));
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));

        timelineService.recordStage(reversalId, 8, "POSTED", "PASSED", "Reversal posted", Instant.now(), "POSTED", null);

        assertThat(reversal.getStatus()).isEqualTo("POSTED");
        assertThat(testTxn.getStatus()).isEqualTo("REVERSED");
        verify(transactionRepository).save(testTxn);
    }

    @Test
    @DisplayName("recordStage leaves a reversed transfer REVERSED when one of its own late stages arrives")
    void testRecordStageDoesNotUndoReversed() {
        testTxn.setStatus("REVERSED");
        when(stageEventRepository.findByTransactionIdAndStageNo(transactionId, 10)).thenReturn(Optional.empty());
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));

        timelineService.recordStage(transactionId, 10, "NOTIFICATION", "PASSED", null, Instant.now(), "POSTED", null);

        assertThat(testTxn.getStatus()).isEqualTo("REVERSED");
    }

    @Test
    @DisplayName("recordStage does not put a finished transfer back in progress when an early stage arrives late")
    void testRecordStageLateEarlyStageKeepsFinishedStatus() {
        testTxn.setStatus("FAILED");
        testTxn.setFailureCode("HIGH_RISK");
        when(stageEventRepository.findByTransactionIdAndStageNo(transactionId, 3)).thenReturn(Optional.empty());
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));

        timelineService.recordStage(transactionId, 3, "AUTHENTICATED", "PASSED", null, Instant.now(), "PROCESSING", null);

        verify(stageEventRepository).save(any(TxnStageEvent.class));
        assertThat(testTxn.getStatus()).isEqualTo("FAILED");
        assertThat(testTxn.getFailureCode()).isEqualTo("HIGH_RISK");
    }

    @Test
    @DisplayName("getExecutionStatus builds Ingress -> Screened -> Committed -> Notified execution state flow")
    void testGetExecutionStatus() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(testTxn));
        when(stageEventRepository.findByTransactionIdOrderByStageNoAsc(transactionId)).thenReturn(List.of(
                TxnStageEvent.builder().stageNo(1).stageName("INITIATED").outcome("PASSED").occurredAt(Instant.now()).build(),
                TxnStageEvent.builder().stageNo(2).stageName("VALIDATED").outcome("PASSED").occurredAt(Instant.now()).build(),
                TxnStageEvent.builder().stageNo(3).stageName("AUTHENTICATED").outcome("PASSED").occurredAt(Instant.now()).build()
        ));

        com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto result =
                timelineService.getExecutionStatus(transactionId.toString());

        assertThat(result.getTransactionId()).isEqualTo(transactionId);
        assertThat(result.getExecutionFlow()).isEqualTo("Ingress -> Screened -> Committed -> Notified");
        assertThat(result.getExecutionStates()).hasSize(4);
        assertThat(result.getExecutionStates().get(0).getName()).isEqualTo("Ingress");
        assertThat(result.getExecutionStates().get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getExecutionStates().get(1).getName()).isEqualTo("Screened");
        assertThat(result.getExecutionStates().get(1).getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.getExecutionStates().get(2).getName()).isEqualTo("Committed");
        assertThat(result.getExecutionStates().get(2).getStatus()).isEqualTo("PENDING");
        assertThat(result.getExecutionStates().get(3).getName()).isEqualTo("Notified");
        assertThat(result.getExecutionStates().get(3).getStatus()).isEqualTo("PENDING");
    }
}
