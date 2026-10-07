package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.dto.CaptureHoldRequestDto;
import com.group2.fse.ledger_service.dto.DebitCreditRequestDto;
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
 * Every posting must move ledger_balance with available_balance. Real service classes over
 * in-memory repositories that apply the 4-decimal rounding of Balance.onUpdate() and the two
 * BALANCE check constraints from database/oracle/02_create_tables.sql.
 */
class DoubleEntryBalanceTest {

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
            journal.add(t);
            return t;
        });
        when(transactionRepository.findByOriginalReferenceNo(anyString())).thenAnswer(i -> {
            String ref = i.getArgument(0);
            return journal.stream()
                    .filter(t -> t.getReferenceNo().equals(ref) || t.getReferenceNo().startsWith(ref + "-"))
                    .toList();
        });

        DualWriteLedgerAuditService audit = mock(DualWriteLedgerAuditService.class);
        PostingEngineImpl postingEngine = new PostingEngineImpl(balanceRepository, transactionRepository, audit);
        holdService = new HoldServiceImpl(holdRepository, balanceRepository, postingEngine, mock(OutboxService.class));
        balanceService = new AccountBalanceServiceImpl(balanceRepository, transactionRepository, audit,
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

    private DebitCreditRequestDto mutation(String ref) {
        return DebitCreditRequestDto.builder().accountId(1L).amount(new BigDecimal("100.00")).referenceNo(ref).build();
    }

    private TransferRequestDto transfer(String ref) {
        return TransferRequestDto.builder().sourceAccountId(1L).destinationAccountId(2L)
                .amount(new BigDecimal("100.00")).referenceNo(ref).build();
    }

    @Test
    void staffCredit_raisesBothBalances() {
        balanceService.mutateCredit(mutation("CR-1"), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(avail(1L)).isEqualByComparingTo("1100.0000");
        assertThat(ledger(1L)).isEqualByComparingTo("1100.0000");
    }

    @Test
    void staffDebit_lowersBothBalances() {
        balanceService.mutateDebit(mutation("DR-1"), 9L, "127.0.0.1");

        assertThat(avail(1L)).isEqualByComparingTo("900.0000");
        assertThat(ledger(1L)).isEqualByComparingTo("900.0000");
    }

    @Test
    void legacyDebitAndCredit_moveBothBalances() {
        balanceService.executeCredit(1L, new BigDecimal("100.00"), "CR-2");
        balanceService.executeDebit(1L, new BigDecimal("30.00"), "DR-2");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(avail(1L)).isEqualByComparingTo("1070.0000");
        assertThat(ledger(1L)).isEqualByComparingTo("1070.0000");
    }

    @Test
    void staffTransfer_movesBothBalancesOnBothSides() {
        balanceService.executeTransfer(transfer("TRF-1"), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(avail(1L)).isEqualByComparingTo("900.0000");
        assertThat(ledger(1L)).isEqualByComparingTo("900.0000");
        assertThat(avail(2L)).isEqualByComparingTo("600.0000");
        assertThat(ledger(2L)).isEqualByComparingTo("600.0000");
    }

    @Test
    void staffDebitWhileAHoldIsOpen_keepsTheHeldAmountBetweenTheTwoBalances() {
        holdService.placeHold(PlaceHoldRequestDto.builder().referenceNo("TX-20261007-AAAA0002").accountId(1L)
                .amount(new BigDecimal("200.00")).build(), null);

        balanceService.mutateDebit(mutation("DR-3"), 9L, "127.0.0.1");

        assertThat(avail(1L)).isEqualByComparingTo("700.0000");
        assertThat(ledger(1L)).isEqualByComparingTo("900.0000");
    }

    @Test
    void reversalOfCapturedTransfer_restoresBothBalancesOnBothSides() {
        Long holdId = holdService.placeHold(PlaceHoldRequestDto.builder().referenceNo("TX-20261007-AAAA0001")
                .accountId(1L).amount(new BigDecimal("100.00")).build(), null).getHoldId();
        holdService.captureHold(holdId, CaptureHoldRequestDto.builder().destinationAccountId(2L).build(), null);
        checkViolations.clear();

        reversalService.processReversal(ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261007-AAAA0001").reversalReferenceNo("RV-1").build(), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(avail(1L)).isEqualByComparingTo("1000.0000");
        assertThat(ledger(1L)).isEqualByComparingTo("1000.0000");
        assertThat(avail(2L)).isEqualByComparingTo("500.0000");
        assertThat(ledger(2L)).isEqualByComparingTo("500.0000");
    }

    @Test
    void reversalOfStaffTransfer_restoresBothBalancesOnBothSides() {
        balanceService.executeTransfer(transfer("TRF-2"), 9L, "127.0.0.1");

        reversalService.processReversal(ReversalRequestDto.builder()
                .originalReferenceNo("TRF-2").reversalReferenceNo("RV-2").build(), 9L, "127.0.0.1");

        assertThat(checkViolations).as("Oracle CHECK violations").isEmpty();
        assertThat(ledger(1L)).isEqualByComparingTo("1000.0000");
        assertThat(ledger(2L)).isEqualByComparingTo("500.0000");
    }

    @Test
    void accountWithNoBalanceRow_isNotFoundAndNeverReadByBalanceId() {
        assertThatThrownBy(() -> balanceService.mutateCredit(
                DebitCreditRequestDto.builder().accountId(77L).amount(new BigDecimal("100.00")).referenceNo("CR-4")
                        .build(), 9L, "127.0.0.1"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> balanceService.executeTransfer(
                TransferRequestDto.builder().sourceAccountId(1L).destinationAccountId(77L)
                        .amount(new BigDecimal("100.00")).referenceNo("TRF-4").build(), 9L, "127.0.0.1"))
                .isInstanceOf(AccountNotFoundException.class);

        verify(balanceRepository, never()).findById(anyLong());
        assertThat(avail(1L)).isEqualByComparingTo("1000.0000");
    }
}
