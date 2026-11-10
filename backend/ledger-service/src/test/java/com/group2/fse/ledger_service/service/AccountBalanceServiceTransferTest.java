package com.group2.fse.ledger_service.service;

import com.group2.fse.ledger_service.dto.BalanceResponseDto;
import com.group2.fse.ledger_service.dto.DebitCreditRequestDto;
import com.group2.fse.ledger_service.dto.DebitCreditResponseDto;
import com.group2.fse.ledger_service.dto.TransferRequestDto;
import com.group2.fse.ledger_service.dto.TransferResponseDto;
import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.exception.AccountNotActiveException;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.exception.DualWriteAuditException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.event.LedgerMutationEvent;
import com.group2.fse.ledger_service.event.LedgerTransferEvent;
import com.group2.fse.ledger_service.posting.PostingEngineImpl;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.service.impl.AccountBalanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Double-Entry Atomic Transfer & Concurrency Defense Service Tests (FSE-306)")
class AccountBalanceServiceTransferTest {

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DualWriteLedgerAuditService dualWriteAuditService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    private AccountBalanceServiceImpl balanceService;

    private Balance sourceBalance;
    private Balance destBalance;

    @BeforeEach
    void setUp() {
        // Real posting engine over the mocked repositories: the service no longer does its own balance math
        balanceService = new AccountBalanceServiceImpl(
                balanceRepository,
                new PostingEngineImpl(balanceRepository, transactionRepository, dualWriteAuditService),
                applicationEventPublisher);
        Account sourceAccount = Account.builder().accountId(10L).accountNumber("ACC-10").build();
        sourceBalance = Balance.builder()
                .balanceId(10L)
                .account(sourceAccount)
                .availableBalance(new BigDecimal("5000.0000"))
                .ledgerBalance(new BigDecimal("5000.0000"))
                .build();

        Account destAccount = Account.builder().accountId(20L).accountNumber("ACC-20").build();
        destBalance = Balance.builder()
                .balanceId(20L)
                .account(destAccount)
                .availableBalance(new BigDecimal("1000.0000"))
                .ledgerBalance(new BigDecimal("1000.0000"))
                .build();
    }

    @Test
    @DisplayName("Should successfully execute atomic transfer between accounts in ordered deadlock-free lock sequence")
    void shouldExecuteAtomicTransferSuccessfully() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(20L)
                .amount(new BigDecimal("2000.0000"))
                .referenceNo("TRF-20260926-001")
                .remarks("Supplier Payment")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setTransactionId(txn.getTransactionType().equals("DEBIT") ? 801L : 802L);
            return txn;
        });

        TransferResponseDto response = balanceService.executeTransfer(request, 999L, "192.168.1.50");

        assertThat(response).isNotNull();
        assertThat(response.getTransferReference()).isEqualTo("TRF-20260926-001");
        assertThat(response.getSourceAccountId()).isEqualTo(10L);
        assertThat(response.getDestinationAccountId()).isEqualTo(20L);
        assertThat(response.getAmount()).isEqualByComparingTo("2000.0000");
        assertThat(response.getSourcePreviousBalance()).isEqualByComparingTo("5000.0000");
        assertThat(response.getSourceNewBalance()).isEqualByComparingTo("3000.0000");
        assertThat(response.getDestinationPreviousBalance()).isEqualByComparingTo("1000.0000");
        assertThat(response.getDestinationNewBalance()).isEqualByComparingTo("3000.0000");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");

        // Verify balances updated in repository
        verify(balanceRepository).save(sourceBalance);
        verify(balanceRepository).save(destBalance);
        assertThat(sourceBalance.getAvailableBalance()).isEqualByComparingTo("3000.0000");
        assertThat(destBalance.getAvailableBalance()).isEqualByComparingTo("3000.0000");

        // Verify two paired transactions saved in Oracle
        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txnCaptor.capture());
        assertThat(txnCaptor.getAllValues()).extracting("transactionType")
                .containsExactly("DEBIT", "CREDIT");
        assertThat(txnCaptor.getAllValues()).extracting("referenceNo")
                .containsExactly("TRF-20260926-001-D", "TRF-20260926-001-C");

        // Verify dual audit writes to PostgreSQL for both legs
        verify(dualWriteAuditService).recordMutationAudit(
                eq(801L), eq(10L), eq("DEBIT"), eq(new BigDecimal("2000.0000")),
                eq(new BigDecimal("5000.0000")), eq(new BigDecimal("3000.0000")), eq(999L), eq("192.168.1.50")
        );
        verify(dualWriteAuditService).recordMutationAudit(
                eq(802L), eq(20L), eq("CREDIT"), eq(new BigDecimal("2000.0000")),
                eq(new BigDecimal("1000.0000")), eq(new BigDecimal("3000.0000")), eq(999L), eq("192.168.1.50")
        );
    }

    @Test
    @DisplayName("Should execute atomic transfer correctly when source ID is greater than destination ID (descending IDs lock in ascending order)")
    void shouldLockAscendingEvenWhenSourceIdIsGreater() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(20L)
                .destinationAccountId(10L)
                .amount(new BigDecimal("500.0000"))
                .referenceNo("TRF-20260926-002")
                .build();

        // 10L is min, 20L is max
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(destBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(sourceBalance));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResponseDto response = balanceService.executeTransfer(request, 1L, "127.0.0.1");

        assertThat(response.getSourceNewBalance()).isEqualByComparingTo("4500.0000");
        assertThat(response.getDestinationNewBalance()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("Should reject transfer when source account has insufficient funds")
    void shouldRejectTransferWithInsufficientFunds() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(20L)
                .amount(new BigDecimal("99999.0000"))
                .referenceNo("TRF-20260926-003")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));

        assertThatThrownBy(() -> balanceService.executeTransfer(request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient");

        verify(balanceRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(dualWriteAuditService, never()).recordMutationAudit(anyLong(), anyLong(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject transfer when source and destination are identical")
    void shouldRejectTransferToSameAccount() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(10L)
                .amount(new BigDecimal("100.0000"))
                .referenceNo("TRF-SAME-ACC")
                .build();

        assertThatThrownBy(() -> balanceService.executeTransfer(request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("distinct");
    }

    @Test
    @DisplayName("Should reject transfer when account balance record does not exist")
    void shouldRejectTransferWhenAccountMissing() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(999L)
                .amount(new BigDecimal("100.0000"))
                .referenceNo("TRF-MISSING")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.executeTransfer(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should propagate DualWriteAuditException to trigger Spring @Transactional rollback")
    void shouldPropagateDualWriteFailure() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(20L)
                .amount(new BigDecimal("100.0000"))
                .referenceNo("TRF-AUDIT-FAIL")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new DualWriteAuditException("PostgreSQL connection timeout", null))
                .when(dualWriteAuditService).recordMutationAudit(anyLong(), anyLong(), anyString(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> balanceService.executeTransfer(request))
                .isInstanceOf(DualWriteAuditException.class);
    }

    @Test
    @DisplayName("Should mutateDebit and mutateCredit returning DebitCreditResponseDto")
    void shouldMutateDebitAndCredit() {
        DebitCreditRequestDto debitReq = DebitCreditRequestDto.builder()
                .accountId(10L)
                .amount(new BigDecimal("500.0000"))
                .referenceNo("DEB-001")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction txn = inv.getArgument(0);
            txn.setTransactionId(777L);
            return txn;
        });

        DebitCreditResponseDto debitRes = balanceService.mutateDebit(debitReq, 1L, "127.0.0.1");
        assertThat(debitRes.getTransactionId()).isEqualTo(777L);
        assertThat(debitRes.getNewBalance()).isEqualByComparingTo("4500.0000");

        DebitCreditRequestDto creditReq = DebitCreditRequestDto.builder()
                .accountId(20L)
                .amount(new BigDecimal("1000.0000"))
                .referenceNo("CRED-001")
                .build();

        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));
        DebitCreditResponseDto creditRes = balanceService.mutateCredit(creditReq, 1L, "127.0.0.1");
        assertThat(creditRes.getNewBalance()).isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("Should retrieve read-only balance without write locking")
    void shouldGetAccountBalance() {
        when(balanceRepository.findReadOnlyByAccountId(10L)).thenReturn(Optional.of(sourceBalance));

        BalanceResponseDto balanceDto = balanceService.getAccountBalance(10L);

        assertThat(balanceDto.getAccountId()).isEqualTo(10L);
        assertThat(balanceDto.getCurrency()).isEqualTo("PHP");
        assertThat(balanceDto.getAvailableBalance()).isEqualByComparingTo("5000.0000");
        assertThat(balanceDto.getIsCached()).isFalse();
    }

    @Test
    @DisplayName("Should publish both mutation events and transfer event on transfer completion")
    void shouldPublishDomainEventsOnTransferCompletion() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(20L)
                .amount(new BigDecimal("1000.0000"))
                .referenceNo("TRF-EVENT-001")
                .remarks("Event test transfer")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction txn = inv.getArgument(0);
            txn.setTransactionId(900L);
            return txn;
        });

        balanceService.executeTransfer(request, 1L, "127.0.0.1");

        // Verify two LedgerMutationEvents (one per leg) and one LedgerTransferEvent published
        verify(applicationEventPublisher, times(2)).publishEvent(any(LedgerMutationEvent.class));
        verify(applicationEventPublisher, times(1)).publishEvent(any(LedgerTransferEvent.class));
    }

    @Test
    @DisplayName("Should not publish any events when transfer fails due to insufficient funds")
    void shouldNotPublishEventsOnInsufficientFunds() {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(20L)
                .amount(new BigDecimal("99999.0000"))
                .referenceNo("TRF-FAIL-001")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));

        assertThatThrownBy(() -> balanceService.executeTransfer(request, 1L, "127.0.0.1"))
                .isInstanceOf(InsufficientFundsException.class);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should reject transfer and throw AccountNotActiveException when source account is FROZEN")
    void shouldRejectTransferWhenSourceAccountIsFrozen() {
        sourceBalance.getAccount().setStatus("FROZEN");

        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(20L)
                .amount(new BigDecimal("100.0000"))
                .referenceNo("TRF-FROZEN-001")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));

        assertThatThrownBy(() -> balanceService.executeTransfer(request, 1L, "127.0.0.1"))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("FROZEN");
    }

    @Test
    @DisplayName("Should reject transfer and throw AccountNotActiveException when destination account is FROZEN")
    void shouldRejectTransferWhenDestinationAccountIsFrozen() {
        destBalance.getAccount().setStatus("FROZEN");

        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(10L)
                .destinationAccountId(20L)
                .amount(new BigDecimal("100.0000"))
                .referenceNo("TRF-FROZEN-002")
                .build();

        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(sourceBalance));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(destBalance));

        assertThatThrownBy(() -> balanceService.executeTransfer(request, 1L, "127.0.0.1"))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("FROZEN");
    }
}
