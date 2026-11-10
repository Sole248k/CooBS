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
                .ledgerBalance(new BigDecimal("5000.0000"))
                .build();

        balanceB = Balance.builder()
                .balanceId(2L)
                .account(accountB)
                .availableBalance(new BigDecimal("2000.0000"))
                .ledgerBalance(new BigDecimal("2000.0000"))
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

    // ---- ledger review L1 to L4: every posting moves both balances --------------------------

    @Test
    @DisplayName("applyDebit: lowers ledger balance together with available balance")
    void testApplyDebit_MovesLedgerBalance() {
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Balance updated = postingEngine.applyDebit(balanceA, new BigDecimal("1500.0000"));

        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("3500.0000");
        assertThat(updated.getLedgerBalance()).isEqualByComparingTo("3500.0000");
    }

    @Test
    @DisplayName("applyCredit: raises ledger balance together with available balance")
    void testApplyCredit_MovesLedgerBalance() {
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Balance updated = postingEngine.applyCredit(balanceA, new BigDecimal("1000.0000"));

        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("6000.0000");
        assertThat(updated.getLedgerBalance()).isEqualByComparingTo("6000.0000");
    }

    @Test
    @DisplayName("applyDebit: an open hold keeps its gap between ledger and available")
    void testApplyDebit_KeepsOpenHoldGap() {
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        balanceA.setAvailableBalance(new BigDecimal("4000.0000")); // 1000 on hold

        Balance updated = postingEngine.applyDebit(balanceA, new BigDecimal("1500.0000"));

        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("2500.0000");
        assertThat(updated.getLedgerBalance()).isEqualByComparingTo("3500.0000");
    }

    @Test
    @DisplayName("postDebit and postCredit: move both balances")
    void testPostDebitAndCredit_MoveBothBalances() {
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(balanceB));
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostingLegResult debit = postingEngine.postDebit(10L, new BigDecimal("500.00"), "D-1", 1L, "127.0.0.1", true);
        PostingLegResult credit = postingEngine.postCredit(20L, new BigDecimal("500.00"), "C-1", 1L, "127.0.0.1", true);

        assertThat(debit.getNewBalance()).isEqualByComparingTo("4500.0000");
        assertThat(balanceA.getAvailableBalance()).isEqualByComparingTo("4500.0000");
        assertThat(balanceA.getLedgerBalance()).isEqualByComparingTo("4500.0000");
        assertThat(credit.getNewBalance()).isEqualByComparingTo("2500.0000");
        assertThat(balanceB.getAvailableBalance()).isEqualByComparingTo("2500.0000");
        assertThat(balanceB.getLedgerBalance()).isEqualByComparingTo("2500.0000");
    }

    @Test
    @DisplayName("postTransfer: moves both balances on both legs")
    void testPostTransfer_MovesBothBalancesOnBothLegs() {
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));
        when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(balanceB));
        when(balanceRepository.save(any(Balance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postingEngine.postTransfer(10L, 20L, new BigDecimal("500.00"), "TRF-1", 1L, "127.0.0.1");

        assertThat(balanceA.getAvailableBalance()).isEqualByComparingTo("4500.0000");
        assertThat(balanceA.getLedgerBalance()).isEqualByComparingTo("4500.0000");
        assertThat(balanceB.getAvailableBalance()).isEqualByComparingTo("2500.0000");
        assertThat(balanceB.getLedgerBalance()).isEqualByComparingTo("2500.0000");
    }

    @Test
    @DisplayName("post: applies each signed delta to its own balance and saves once")
    void testPost_AppliesAvailableAndLedgerDeltasIndependently() {
        // place hold: available only
        postingEngine.post(balanceA, new BigDecimal("-500.00"), BigDecimal.ZERO);
        assertThat(balanceA.getAvailableBalance()).isEqualTo(new BigDecimal("4500.0000"));
        assertThat(balanceA.getLedgerBalance()).isEqualTo(new BigDecimal("5000.0000"));

        // capture, source side: ledger only
        postingEngine.post(balanceA, BigDecimal.ZERO, new BigDecimal("-500.00"));
        assertThat(balanceA.getAvailableBalance()).isEqualTo(new BigDecimal("4500.0000"));
        assertThat(balanceA.getLedgerBalance()).isEqualTo(new BigDecimal("4500.0000"));

        // capture, destination side: both
        Balance returned = postingEngine.post(balanceB, new BigDecimal("500.00"), new BigDecimal("500.00"));
        assertThat(returned).isSameAs(balanceB);
        assertThat(balanceB.getAvailableBalance()).isEqualTo(new BigDecimal("2500.0000"));
        assertThat(balanceB.getLedgerBalance()).isEqualTo(new BigDecimal("2500.0000"));

        verify(balanceRepository, times(2)).save(balanceA);
        verify(balanceRepository, times(1)).save(balanceB);
    }

    @Test
    @DisplayName("post: rejects a missing delta instead of guessing")
    void testPost_NullDelta_Rejected() {
        assertThatThrownBy(() -> postingEngine.post(balanceA, null, BigDecimal.ZERO))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> postingEngine.post(balanceA, BigDecimal.ZERO, null))
                .isInstanceOf(InvalidTransactionException.class);

        verify(balanceRepository, never()).save(any());
    }

    // ---- wrong-account fallback: account_id is never looked up as balance_id -----------------

    @Test
    @DisplayName("lockAccount: a missing balance row is AccountNotFoundException, never a balance_id lookup")
    void testLockAccount_MissingBalanceRow_DoesNotFallBackToBalanceId() {
        when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.empty());
        lenient().when(balanceRepository.findById(10L)).thenReturn(Optional.of(balanceB));

        assertThatThrownBy(() -> postingEngine.lockAccount(10L))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> postingEngine.lockAccount(10L, false))
                .isInstanceOf(AccountNotFoundException.class);

        verify(balanceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("lockAccountsInOrder: a missing balance row on either side is AccountNotFoundException")
    void testLockAccountsInOrder_MissingBalanceRow_DoesNotFallBackToBalanceId() {
        lenient().when(balanceRepository.findByAccountId(10L)).thenReturn(Optional.of(balanceA));
        lenient().when(balanceRepository.findByAccountId(20L)).thenReturn(Optional.of(balanceB));
        when(balanceRepository.findByAccountId(5L)).thenReturn(Optional.empty());
        when(balanceRepository.findByAccountId(30L)).thenReturn(Optional.empty());
        lenient().when(balanceRepository.findById(5L)).thenReturn(Optional.of(balanceB));
        lenient().when(balanceRepository.findById(30L)).thenReturn(Optional.of(balanceB));

        assertThatThrownBy(() -> postingEngine.lockAccountsInOrder(5L, 10L))
                .isInstanceOf(AccountNotFoundException.class).hasMessageContaining("5");
        assertThatThrownBy(() -> postingEngine.lockAccountsInOrder(10L, 30L))
                .isInstanceOf(AccountNotFoundException.class).hasMessageContaining("30");

        verify(balanceRepository, never()).findById(any());
    }
}