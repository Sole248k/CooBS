package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.dto.CaptureHoldRequestDto;
import com.group2.fse.ledger_service.dto.DebitCreditRequestDto;
import com.group2.fse.ledger_service.dto.MutationResult;
import com.group2.fse.ledger_service.dto.PlaceHoldRequestDto;
import com.group2.fse.ledger_service.dto.TransferRequestDto;
import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Hold;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.HoldRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.reversal.dto.ReversalRequestDto;
import com.group2.fse.ledger_service.reversal.service.impl.ReversalServiceImpl;
import com.group2.fse.ledger_service.service.DualWriteLedgerAuditService;
import com.group2.fse.ledger_service.service.OutboxService;
import com.group2.fse.ledger_service.service.impl.AccountBalanceServiceImpl;
import com.group2.fse.ledger_service.service.impl.HoldServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ledger review defects L1 to L4 and the wrong-account fallback (audit finding 6).
 * Real service classes over in-memory repositories. The in-memory BALANCE store applies the
 * same 4dp HALF_UP rounding as Balance.onUpdate() and the two Oracle CHECK constraints from
 * database/oracle/02_create_tables.sql.
 */
class LedgerPostingBothBalancesTest {

    /** A balance_id that happens to equal a missing account_id; the row belongs to account 2. */
    private static final Long MISSING_ACCOUNT_ID = 99L;

    private final Map<Long, Balance> balances = new HashMap<>();
    private final Map<Long, Hold> holds = new HashMap<>();
    private final List<Transaction> journal = new ArrayList<>();
    private final List<String> checkViolations = new ArrayList<>();
    private long seq = 1000;

    private BalanceRepository balanceRepository;
    private HoldServiceImpl holdService;
    private AccountBalanceServiceImpl balanceService;
    private ReversalServiceImpl reversalService;

    @BeforeEach
    void setUp() {
        balanceRepository = mock(BalanceRepository.class);
        HoldRepository holdRepository = mock(HoldRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);

        when(balanceRepository.findByAccountId(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(balances.get(i.<Long>getArgument(0))));
        when(balanceRepository.findReadOnlyByAccountId(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(balances.get(i.<Long>getArgument(0))));
        when(balanceRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(balanceRepository.save(any(Balance.class))).thenAnswer(i -> {
            Balance b = i.getArgument(0);
            b.setAvailableBalance(b.getAvailableBalance().setScale(4, RoundingMode.HALF_UP));
            b.setLedgerBalance(b.getLedgerBalance().setScale(4, RoundingMode.HALF_UP));
            if (b.getAvailableBalance().signum() < 0) {
                checkViolations.add("chk_ora_bal_non_neg acct " + b.getAccountId());
            }
            if (b.getLedgerBalance().compareTo(b.getAvailableBalance()) < 0) {
                checkViolations.add("chk_ora_ledger_ge_avail acct " + b.getAccountId()
                        + " ledger=" + b.getLedgerBalance() + " available=" + b.getAvailableBalance());
            }
            return b;
        });

        when(holdRepository.findByReferenceNo(anyString())).thenAnswer(i -> holds.values().stream()
                .filter(h -> h.getReferenceNo().equals(i.getArgument(0))).findFirst());
        when(holdRepository.findById(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(holds.get(i.<Long>getArgument(0))));
        when(holdRepository.save(any(Hold.class))).thenAnswer(i -> {
            Hold h = i.getArgument(0);
            if (h.getHoldId() == null) {
                h.setHoldId(++seq);
            }
            holds.put(h.getHoldId(), h);
            return h;
        });

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setTransactionId(++seq);
            t.setAmount(t.getAmount().setScale(4, RoundingMode.HALF_UP));
            journal.add(t);
            return t;
        });
        when(transactionRepository.existsByReferenceNo(anyString())).thenAnswer(i -> journal.stream()
                .anyMatch(t -> t.getReferenceNo().equals(i.getArgument(0))));
        when(transactionRepository.findByOriginalReferenceNo(anyString())).thenAnswer(i -> {
            String ref = i.getArgument(0);
            return journal.stream()
                    .filter(t -> t.getReferenceNo().equals(ref) || t.getReferenceNo().startsWith(ref + "-"))
                    .toList();
        });
        when(transactionRepository.findByReferenceNo(anyString())).thenAnswer(i -> journal.stream()
                .filter(t -> t.getReferenceNo().equals(i.getArgument(0))).findFirst());
        when(transactionRepository.existsByReversesTransaction_TransactionId(anyLong())).thenReturn(false);

        DualWriteLedgerAuditService audit = mock(DualWriteLedgerAuditService.class);
        PostingEngineImpl postingEngine = new PostingEngineImpl(balanceRepository, transactionRepository, audit);
        holdService = new HoldServiceImpl(holdRepository, balanceRepository, postingEngine, mock(OutboxService.class));
        balanceService = new AccountBalanceServiceImpl(balanceRepository, postingEngine,
                mock(ApplicationEventPublisher.class));
        reversalService = new ReversalServiceImpl(postingEngine, transactionRepository);

        open(1L, "1000.0000");
        open(2L, "500.0000");
    }

    private void open(Long id, String amount) {
        Account account = Account.builder().accountId(id).accountNumber("ACC" + id).status("ACTIVE").build();
        balances.put(id, Balance.builder().balanceId(id).account(account)
                .availableBalance(new BigDecimal(amount)).ledgerBalance(new BigDecimal(amount)).build());
    }

    private BigDecimal avail(Long id) {
        return balances.get(id).getAvailableBalance();
    }

    private BigDecimal ledger(Long id) {
        return balances.get(id).getLedgerBalance();
    }

    /** The old fallback looked a missing account up by balance_id and found account 2's row. */
    private void balanceIdOfAccount2CollidesWithMissingAccountId() {
        when(balanceRepository.findById(MISSING_ACCOUNT_ID)).thenReturn(Optional.of(balances.get(2L)));
    }

    private void assertAccount2Untouched() {
        assertThat(avail(2L)).isEqualByComparingTo("500.0000");
        assertThat(ledger(2L)).isEqualByComparingTo("500.0000");
        verify(balanceRepository, never()).save(any(Balance.class));
        verify(balanceRepository, never()).findById(anyLong());
    }

    private DebitCreditRequestDto staffRequest(Long accountId, String ref) {
        return DebitCreditRequestDto.builder().accountId(accountId)
                .amount(new BigDecimal("100.00")).referenceNo(ref).build();
    }

    // ---- L1 to L4: double-entry ------------------------------------------------------------

    @Test
    void staffCredit_keepsLedgerBalanceInStepWithAvailable() {
        balanceService.mutateCredit(staffRequest(1L, "CR-1"), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(avail(1L)).isEqualByComparingTo("1100.0000");
        assertThat(ledger(1L)).isEqualByComparingTo(avail(1L));
    }

    @Test
    void staffDebit_reducesLedgerBalanceToo() {
        balanceService.mutateDebit(staffRequest(1L, "DR-1"), 9L, "127.0.0.1");

        assertThat(avail(1L)).isEqualByComparingTo("900.0000");
        assertThat(ledger(1L)).as("no holds outstanding, so ledger must equal available")
                .isEqualByComparingTo(avail(1L));
    }

    @Test
    void staffTransfer_movesLedgerBalanceOnBothSides() {
        balanceService.executeTransfer(TransferRequestDto.builder().sourceAccountId(1L).destinationAccountId(2L)
                .amount(new BigDecimal("100.00")).referenceNo("TRF-1").build(), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(avail(1L)).isEqualByComparingTo("900.0000");
        assertThat(avail(2L)).isEqualByComparingTo("600.0000");
        assertThat(ledger(1L)).isEqualByComparingTo(avail(1L));
        assertThat(ledger(2L)).isEqualByComparingTo(avail(2L));
    }

    @Test
    void reversalOfCapturedTransfer_restoresBothBalancesOnBothSides() {
        Long holdId = holdService.placeHold(PlaceHoldRequestDto.builder().referenceNo("TX-20261006-AAAA0001")
                .accountId(1L).amount(new BigDecimal("100.00")).build(), null).getHoldId();
        holdService.captureHold(holdId, CaptureHoldRequestDto.builder().destinationAccountId(2L).build(), null);
        checkViolations.clear();

        reversalService.processReversal(ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261006-AAAA0001").reversalReferenceNo("RV-1").build(), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(ledger(1L)).isEqualByComparingTo("1000.0000");
        assertThat(ledger(2L)).isEqualByComparingTo("500.0000");
        assertThat(avail(1L)).isEqualByComparingTo("1000.0000");
        assertThat(avail(2L)).isEqualByComparingTo("500.0000");
    }

    @Test
    void reversalOfStaffDebit_restoresBothBalances() {
        balanceService.mutateDebit(staffRequest(1L, "DR-2"), 9L, "127.0.0.1");

        reversalService.processReversal(ReversalRequestDto.builder()
                .originalReferenceNo("DR-2").reversalReferenceNo("RV-2").build(), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(avail(1L)).isEqualByComparingTo("1000.0000");
        assertThat(ledger(1L)).isEqualByComparingTo("1000.0000");
    }

    @Test
    void legacyExecuteDebitAndCredit_moveBothBalances() {
        balanceService.executeDebit(1L, new BigDecimal("100.00"), "LEG-D");
        balanceService.executeCredit(2L, new BigDecimal("100.00"), "LEG-C");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(ledger(1L)).isEqualByComparingTo("900.0000");
        assertThat(ledger(2L)).isEqualByComparingTo("600.0000");
    }

    // ---- wrong-account fallback ------------------------------------------------------------

    @Test
    void staffDebit_onAccountWithoutBalanceRow_isNotFoundAndTouchesNoOtherAccount() {
        balanceIdOfAccount2CollidesWithMissingAccountId();

        assertThatThrownBy(() -> balanceService.mutateDebit(staffRequest(MISSING_ACCOUNT_ID, "DR-X"), 9L, "127.0.0.1"))
                .isInstanceOf(AccountNotFoundException.class);

        assertAccount2Untouched();
    }

    @Test
    void staffCredit_onAccountWithoutBalanceRow_isNotFoundAndTouchesNoOtherAccount() {
        balanceIdOfAccount2CollidesWithMissingAccountId();

        assertThatThrownBy(() -> balanceService.mutateCredit(staffRequest(MISSING_ACCOUNT_ID, "CR-X"), 9L, "127.0.0.1"))
                .isInstanceOf(AccountNotFoundException.class);

        assertAccount2Untouched();
    }

    @Test
    void staffTransfer_toAccountWithoutBalanceRow_isNotFoundAndTouchesNoOtherAccount() {
        balanceIdOfAccount2CollidesWithMissingAccountId();

        assertThatThrownBy(() -> balanceService.executeTransfer(TransferRequestDto.builder()
                .sourceAccountId(1L).destinationAccountId(MISSING_ACCOUNT_ID)
                .amount(new BigDecimal("100.00")).referenceNo("TRF-X").build(), 9L, "127.0.0.1"))
                .isInstanceOf(AccountNotFoundException.class);

        assertAccount2Untouched();
        assertThat(avail(1L)).isEqualByComparingTo("1000.0000");
    }

    @Test
    void legacyExecuteDebitAndCredit_onAccountWithoutBalanceRow_failAndTouchNoOtherAccount() {
        balanceIdOfAccount2CollidesWithMissingAccountId();

        MutationResult debit = balanceService.executeDebit(MISSING_ACCOUNT_ID, new BigDecimal("100.00"), "LEG-DX");
        MutationResult credit = balanceService.executeCredit(MISSING_ACCOUNT_ID, new BigDecimal("100.00"), "LEG-CX");

        assertThat(debit.isSuccess()).isFalse();
        assertThat(debit.getErrorMessage()).isEqualTo("Account balance record not found.");
        assertThat(credit.isSuccess()).isFalse();
        assertThat(credit.getErrorMessage()).isEqualTo("Account balance record not found.");
        assertAccount2Untouched();
    }

    @Test
    void balanceRead_onAccountWithoutBalanceRow_isNotFoundNotAnotherAccountsBalance() {
        balanceIdOfAccount2CollidesWithMissingAccountId();

        assertThatThrownBy(() -> balanceService.getAccountBalance(MISSING_ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);

        verify(balanceRepository, never()).findById(anyLong());
    }
}
