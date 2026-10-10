package com.group2.fse.batch_service.step;

import com.group2.fse.batch_service.entity.DailyAuditSeal;
import com.group2.fse.batch_service.entity.LoanSchedule;
import com.group2.fse.batch_service.repository.DailyAuditSealRepository;
import com.group2.fse.batch_service.repository.LoanScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The steps that move money decide nothing about a balance: they send postings to the ledger
 * and report what it said. A refusal is counted and the run carries on; no answer fails the step.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("End-of-day steps post through the ledger")
class StepsPostThroughLedgerTest {

    private static final LocalDate MONTH_END = LocalDate.of(2026, 9, 30);

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private LedgerPostings ledgerPostings;
    @Mock
    private LoanScheduleRepository loanScheduleRepository;
    @Mock
    private NotificationDispatcherHelper notifications;
    @Mock
    private LedgerAuditHelper accounts;
    @Mock
    private DailyAuditSealRepository seals;

    @SuppressWarnings("unchecked")
    private void candidates(Object[]... rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(rows));
    }

    @SuppressWarnings("unchecked")
    private List<LedgerPostings.Posting> sent() {
        ArgumentCaptor<List<LedgerPostings.Posting>> postings = ArgumentCaptor.forClass(List.class);
        verify(ledgerPostings).post(eq(7L), anyString(), postings.capture());
        return postings.getValue();
    }

    private static Map<String, String> outcomes(String... referenceThenCode) {
        Map<String, String> outcomes = new HashMap<>();
        for (int i = 0; i < referenceThenCode.length; i += 2) {
            outcomes.put(referenceThenCode[i], referenceThenCode[i + 1]);
        }
        return outcomes;
    }

    @Test
    @DisplayName("Fee: debit the member, credit GL 9003, once a month; a refusal is not counted as charged")
    void fee_isPostedThroughTheLedger_andARefusalIsReported() {
        candidates(new Object[]{4L, new BigDecimal("400.00")}, new Object[]{13L, new BigDecimal("30.00")});
        when(ledgerPostings.post(eq(7L), eq("SERVICE_FEES"), anyList()))
                .thenReturn(outcomes("EOD-202609-FEE-000004", "INSUFFICIENT_FUNDS", "EOD-202609-FEE-000013", null));

        BatchStepResult result = new ServiceFeesStep(jdbcTemplate, ledgerPostings).execute(7L, MONTH_END);

        assertThat(sent()).containsExactly(
                new LedgerPostings.Posting("EOD-202609-FEE-000004", 4L, 9003L, new BigDecimal("50.00"), "Monthly maintenance fee"),
                new LedgerPostings.Posting("EOD-202609-FEE-000013", 13L, 9003L, new BigDecimal("30.00"), "Monthly maintenance fee"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getItemsProcessed()).isEqualTo(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(result.getMessage()).contains("1 refused by the ledger");
    }

    @Test
    @DisplayName("Fee: no answer from the ledger fails the step instead of guessing")
    void fee_ledgerSilent_failsTheStep() {
        candidates(new Object[]{4L, new BigDecimal("400.00")});
        when(ledgerPostings.post(eq(7L), eq("SERVICE_FEES"), anyList()))
                .thenThrow(new IllegalStateException("read timed out"));

        assertThatThrownBy(() -> new ServiceFeesStep(jdbcTemplate, ledgerPostings).execute(7L, MONTH_END))
                .hasMessageContaining("read timed out");
    }

    @Test
    @DisplayName("Loan: a schedule is marked PAID only when the ledger posted its instalment")
    void loan_refusedInstalmentStaysDue() {
        LoanSchedule paid = LoanSchedule.builder().scheduleId(11L).loanAccountId(6L)
                .principalDue(new BigDecimal("5000.00")).interestDue(new BigDecimal("450.00")).status("DUE").build();
        LoanSchedule short_ = LoanSchedule.builder().scheduleId(12L).loanAccountId(10L)
                .principalDue(new BigDecimal("900.00")).interestDue(new BigDecimal("100.00")).status("DUE").build();
        when(loanScheduleRepository.findByDueDateLessThanEqualAndStatusIn(eq(MONTH_END), anyList()))
                .thenReturn(List.of(paid, short_));
        when(ledgerPostings.post(eq(7L), eq("LOAN_AMORTIZATION"), anyList()))
                .thenReturn(outcomes("EOD-LN-11", null, "EOD-LN-12", "INSUFFICIENT_FUNDS"));

        BatchStepResult result = new LoanAmortizationStep(loanScheduleRepository, new LedgerAuditHelper(), ledgerPostings)
                .execute(7L, MONTH_END);

        // Loan 6 is repaid from deposit account 4, loan 10 from account 9; both credit GL 9004.
        assertThat(sent()).extracting(LedgerPostings.Posting::debitAccountId, LedgerPostings.Posting::creditAccountId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(4L, 9004L), org.assertj.core.groups.Tuple.tuple(9L, 9004L));
        assertThat(paid.getStatus()).isEqualTo("PAID");
        assertThat(short_.getStatus()).isEqualTo("DUE");
        verify(loanScheduleRepository).save(paid);
        verify(loanScheduleRepository, never()).save(short_);
        assertThat(result.getItemsProcessed()).isEqualTo(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("5450.00");
    }

    @Test
    @DisplayName("Month-end interest: debit GL 9002, credit the member, and notify only for what posted")
    void interest_isPostedThroughTheLedger() {
        candidates(new Object[]{1L, new BigDecimal("82.19")}, new Object[]{2L, new BigDecimal("16.44")});
        when(ledgerPostings.post(eq(7L), eq("INTEREST_ACCRUAL"), anyList()))
                .thenReturn(outcomes("EOD-202609-INT-000001", null, "EOD-202609-INT-000002", "ACCOUNT_NOT_ACTIVE"));

        when(accounts.getAccountInfo(1L)).thenReturn(new LedgerAuditHelper.AccountInfo("ACC_10000001", 1L));

        BatchStepResult result = new InterestAccrualStep(jdbcTemplate, notifications, accounts, ledgerPostings)
                .execute(7L, MONTH_END);

        assertThat(sent()).first().isEqualTo(new LedgerPostings.Posting(
                "EOD-202609-INT-000001", 9002L, 1L, new BigDecimal("82.19"), "Monthly savings interest"));
        assertThat(result.getItemsProcessed()).isEqualTo(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("82.19");
        verify(notifications).dispatchInterestNotification(eq(1L), eq(1L), eq("ACC_10000001"), eq(new BigDecimal("82.19")), eq("EOD-202609-INT-000001"));
        verify(notifications, never()).dispatchInterestNotification(any(), eq(2L), any(), any(), any());
    }

    @Test
    @DisplayName("Month-end interest: when the owner cannot be read the credit counts and nobody is notified")
    void interest_unknownOwner_notifiesNobody() {
        candidates(new Object[]{19L, new BigDecimal("16.44")});
        when(ledgerPostings.post(eq(7L), eq("INTEREST_ACCRUAL"), anyList()))
                .thenReturn(outcomes("EOD-202609-INT-000019", null));
        when(accounts.getAccountInfo(19L)).thenReturn(null);

        BatchStepResult result = new InterestAccrualStep(jdbcTemplate, notifications, accounts, ledgerPostings)
                .execute(7L, MONTH_END);

        assertThat(result.getItemsProcessed()).isEqualTo(1);
        verify(notifications, never()).dispatchInterestNotification(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Reconciliation: equal debits and credits pass, and counter cash is reported beside them")
    void reconciliation_balancedDayPasses() {
        when(accounts.journalTotals(MONTH_END)).thenReturn(new LedgerAuditHelper.JournalTotals(
                5, new BigDecimal("3000.0000"), new BigDecimal("3000.0000"), new BigDecimal("500.0000"), BigDecimal.ZERO));

        BatchStepResult result = new GlReconciliationStep(accounts).execute(7L, MONTH_END);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getItemsProcessed()).isEqualTo(5);
        assertThat(result.getMessage()).isEqualTo("Debits equal credits (5 journal entries; counter cash in 500.00, out 0.00)");
    }

    @Test
    @DisplayName("Reconciliation: a posting whose legs differ fails the step")
    void reconciliation_varianceFailsTheStep() {
        when(accounts.journalTotals(MONTH_END)).thenReturn(new LedgerAuditHelper.JournalTotals(
                4, new BigDecimal("2062.2400"), new BigDecimal("2012.2400"), BigDecimal.ZERO, BigDecimal.ZERO));

        BatchStepResult result = new GlReconciliationStep(accounts).execute(7L, MONTH_END);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getMessage()).isEqualTo("Variance detected: debits 2062.24, credits 2012.24");
    }

    @Test
    @DisplayName("Seal: a day that is over is sealed from its own last audit row, once")
    void seal_pastDayIsSealedOnceFromItsOwnRows() {
        candidates(new Object[]{120L, "ab".repeat(32)});
        when(seals.findByBusinessDate(MONTH_END)).thenReturn(java.util.Optional.empty());

        BatchStepResult first = new AuditSealStep(seals, jdbcTemplate).execute(7L, MONTH_END);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(java.sql.Date.valueOf(MONTH_END)));
        assertThat(sql.getValue()).contains("DATE(event_timestamp) <= ?");
        ArgumentCaptor<DailyAuditSeal> saved = ArgumentCaptor.forClass(DailyAuditSeal.class);
        verify(seals).save(saved.capture());
        assertThat(saved.getValue().getLastAuditId()).isEqualTo(120L);
        assertThat(first.isSuccess()).isTrue();

        // The same day again: the stored seal is reported and nothing is written.
        when(seals.findByBusinessDate(MONTH_END)).thenReturn(java.util.Optional.of(saved.getValue()));
        BatchStepResult again = new AuditSealStep(seals, jdbcTemplate).execute(8L, MONTH_END);

        assertThat(again.getMessage()).contains(saved.getValue().getSealHash().substring(0, 8)).contains("already sealed");
        verify(seals).save(any());
    }

    @Test
    @DisplayName("Seal: no audit row to seal fails the step instead of sealing a made-up one")
    void seal_withoutAuditRowsFails() {
        candidates();
        when(seals.findByBusinessDate(MONTH_END)).thenReturn(java.util.Optional.empty());

        BatchStepResult result = new AuditSealStep(seals, jdbcTemplate).execute(7L, MONTH_END);

        assertThat(result.isSuccess()).isFalse();
        verify(seals, never()).save(any());
    }
}
