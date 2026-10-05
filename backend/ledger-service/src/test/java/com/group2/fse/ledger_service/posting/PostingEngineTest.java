package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.exception.AccountNotActiveException;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.service.DualWriteLedgerAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Posting Engine Extraction Unit Tests - Locking, Balance Math, Holds, and Reversals")
class PostingEngineTest {

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DualWriteLedgerAuditService dualWriteAuditService;

    @InjectMocks
    private PostingEngineImpl postingEngine;

    private Account accountA;
    private Account accountB;
    private Balance balanceA;
    private Balance balanceB;

    @BeforeEach
    void setUp() {
        accountA = Account.builder()
                .accountId(10L)
                .accountNumber("ACC-10")
                .status("ACTIVE")
                .currency("PHP")
                .build();

        accountB = Account.builder()
                .accountId(20L)
                .accountNumber("ACC-20")
                .status("ACTIVE")
                .currency("PHP")
                .build();

        balanceA = Balance.builder()
                .balanceId(1L)
                .account(accountA)
                .availableBalance(new BigDecimal("5000.0000"))
                .build();

        balanceB = Balance.builder()
                .balanceId(2L)
                .account(accountB)
                .availableBalance(new BigDecimal("2000.0000"))
                .build();
    }

    @Test
    @DisplayName("Lock single account successfully with ACTIVE status check")
    void testLockAccount_Success() {
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));

        Balance locked = postingEngine.lockAccount(10L);

        assertThat(locked).isNotNull();
        assertThat(locked.getAccountId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Lock account fails if account is FROZEN")
    void testLockAccount_FrozenAccount_ThrowsAccountNotActiveException() {
        accountA.setStatus("FROZEN");
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));

        assertThatThrownBy(() -> postingEngine.lockAccount(10L))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("FROZEN");
    }

    @Test
    @DisplayName("Ordered lock acquisition ensures min ID is acquired first (Deadlock Prevention)")
    void testLockAccountsInOrder_DeadlockFreeOrdering() {
        // Even when passed in reverse order (20 then 10), min(10) is locked first then max(20)
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(balanceB));

        OrderedAccountLocks locks = postingEngine.lockAccountsInOrder(20L, 10L);

        assertThat(locks.getSourceBalance().getAccountId()).isEqualTo(20L);
        assertThat(locks.getDestinationBalance().getAccountId()).isEqualTo(10L);

        // Verify min(10) then max(20) were retrieved
        verify(balanceRepository).findByAccountId(10L);
        verify(balanceRepository).findByAccountId(20L);
    }

    @Test
    @DisplayName("applyDebit: correctly checks sufficiency and deducts balance")
    void testApplyDebit_Success() {
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Balance updated = postingEngine.applyDebit(balanceA, new BigDecimal("1500.0000"));

        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("3500.0000");
        verify(balanceRepository).save(balanceA);
    }

    @Test
    @DisplayName("applyDebit: throws InsufficientFundsException when debit exceeds balance")
    void testApplyDebit_InsufficientFunds() {
        assertThatThrownBy(() -> postingEngine.applyDebit(balanceA, new BigDecimal("6000.0000")))
                .isInstanceOf(InsufficientFundsException.class);

        verify(balanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyCredit: correctly adds to balance")
    void testApplyCredit_Success() {
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Balance updated = postingEngine.applyCredit(balanceA, new BigDecimal("1000.0000"));

        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("6000.0000");
        verify(balanceRepository).save(balanceA);
    }

    @Test
    @DisplayName("Hold scenario: Share lock and balance math without editing AccountBalanceServiceImpl")
    void testHoldOperation_SharesLockAndBalanceMath() {
        // A Hold service locks the account and reserves funds by applying debit math
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setTransactionId(9001L);
            return txn;
        });

        // Step 1: Lock account
        Balance lockedBalance = postingEngine.lockAccount(10L);
        BigDecimal holdAmount = new BigDecimal("500.0000");

        // Step 2: Validate sufficiency & apply debit math to reserve funds
        postingEngine.validateSufficientFunds(10L, lockedBalance.getAvailableBalance(), holdAmount);
        Balance updatedBalance = postingEngine.applyDebit(lockedBalance, holdAmount);

        // Step 3: Record HOLD transaction leg
        Transaction holdTxn = postingEngine.recordTransaction(
                updatedBalance.getAccount(),
                "HOLD-REF-001",
                "HOLD",
                holdAmount,
                new BigDecimal("5000.0000"),
                updatedBalance.getAvailableBalance(),
                "HELD",
                null
        );

        // Step 4: Record Forensic Audit
        postingEngine.recordAudit(
                holdTxn.getTransactionId(),
                10L,
                "HOLD",
                holdAmount,
                new BigDecimal("5000.0000"),
                updatedBalance.getAvailableBalance(),
                null,
                "127.0.0.1"
        );

        assertThat(updatedBalance.getAvailableBalance()).isEqualByComparingTo("4500.0000");
        verify(balanceRepository).save(lockedBalance);
        verify(transactionRepository).save(any(Transaction.class));
        verify(dualWriteAuditService).recordMutationAudit(
                eq(9001L), eq(10L), eq("HOLD"), eq(holdAmount),
                eq(new BigDecimal("5000.0000")), eq(new BigDecimal("4500.0000")), any(), any()
        );
    }

    @Test
    @DisplayName("Reversal scenario: Share lock and credit math to reverse a debit without editing AccountBalanceServiceImpl")
    void testReversalOperation_SharesLockAndBalanceMath() {
        // A Reversal service locks the account and reverses a debit by applying credit math
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setTransactionId(9002L);
            return txn;
        });

        // Step 1: Lock account
        Balance lockedBalance = postingEngine.lockAccount(10L);
        BigDecimal reversalAmount = new BigDecimal("300.0000");

        // Step 2: Apply credit math to restore funds
        Balance updatedBalance = postingEngine.applyCredit(lockedBalance, reversalAmount);

        // Step 3: Record REVERSAL transaction leg
        Transaction revTxn = postingEngine.recordTransaction(
                updatedBalance.getAccount(),
                "REV-REF-001",
                "REVERSAL",
                reversalAmount,
                new BigDecimal("5000.0000"),
                updatedBalance.getAvailableBalance(),
                "COMPLETED",
                null
        );

        // Step 4: Record Forensic Audit
        postingEngine.recordAudit(
                revTxn.getTransactionId(),
                10L,
                "REVERSAL",
                reversalAmount,
                new BigDecimal("5000.0000"),
                updatedBalance.getAvailableBalance(),
                null,
                "127.0.0.1"
        );

        assertThat(updatedBalance.getAvailableBalance()).isEqualByComparingTo("5300.0000");
        verify(balanceRepository).save(lockedBalance);
        verify(transactionRepository).save(any(Transaction.class));
        verify(dualWriteAuditService).recordMutationAudit(
                eq(9002L), eq(10L), eq("REVERSAL"), eq(reversalAmount),
                eq(new BigDecimal("5000.0000")), eq(new BigDecimal("5300.0000")), any(), any()
        );
    }
}
