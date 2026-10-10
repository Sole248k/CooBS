package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 1: INTEREST_ACCRUAL
 * True Commercial Banking Model:
 * - Daily runs: Calculates and accrues daily interest into GL expense / accrued payable liability (without touching customer cash).
 * - Month-End runs (e.g. Sept 30, Oct 31): Executes monthly interest capitalization, posting the accumulated interest credit
 *   directly into qualifying customer savings accounts and appending to the cryptographic audit store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestAccrualStep implements BatchStepExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationDispatcherHelper notificationDispatcherHelper;
    private final LedgerAuditHelper ledgerAuditHelper;
    private final LedgerPostings ledgerPostings;

    private static final Long GL_INTEREST_EXPENSE = 9002L;
    private static final String SAVINGS = "SAVINGS";

    @Override
    public int getStepNo() {
        return 1;
    }

    @Override
    public String getStepName() {
        return "INTEREST_ACCRUAL";
    }

    @Override
    public BatchStepResult execute(Long batchRunId, LocalDate businessDate) {
        log.info("[BatchRun #{}] Executing Step 1: INTEREST_ACCRUAL for businessDate={}", batchRunId, businessDate);
        boolean isMonthEnd = businessDate.equals(businessDate.with(TemporalAdjusters.lastDayOfMonth()));
        int processed = 0;
        BigDecimal totalInterest = BigDecimal.ZERO;

        if (isMonthEnd) {
            // =========================================================================
            // MONTH-END CAPITALIZATION (EOM Payout into Customer Savings Accounts)
            // =========================================================================
            int daysInMonth = businessDate.lengthOfMonth();
            log.info("[BatchRun #{}] End-of-Month detected for {}. Executing Monthly Interest Capitalization ({} days).",
                    batchRunId, businessDate, daysInMonth);

            List<Object[]> eligibleAccounts = savingsInterest(daysInMonth, businessDate);

            // One reference per account and month: debit 9002 GL_INTEREST_EXPENSE, credit the
            // member. A second run in the same month is answered from the journal and pays nothing more.
            String month = businessDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
            List<LedgerPostings.Posting> postings = new ArrayList<>();
            for (Object[] acc : eligibleAccounts) {
                BigDecimal interestAmount = (BigDecimal) acc[1];
                if (interestAmount.compareTo(BigDecimal.ZERO) > 0) {
                    Long accountId = (Long) acc[0];
                    postings.add(new LedgerPostings.Posting("EOD-" + month + "-INT-" + String.format("%06d", accountId),
                            GL_INTEREST_EXPENSE, accountId, interestAmount, "Monthly savings interest"));
                }
            }

            Map<String, String> outcomes = ledgerPostings.post(batchRunId, getStepName(), postings);

            int refused = 0;
            for (LedgerPostings.Posting posting : postings) {
                String errorCode = outcomes.get(posting.referenceNo());
                if (errorCode != null) {
                    if (!LedgerPostings.ALREADY_USED.equals(errorCode)) {
                        refused++;
                        log.warn("[BatchRun #{}] Interest {} for account #{} refused by the ledger: {}",
                                batchRunId, posting.referenceNo(), posting.creditAccountId(), errorCode);
                    }
                    continue;
                }
                processed++;
                totalInterest = totalInterest.add(posting.amount());

                // Resolve official account number (e.g. ACC_10000001) and customer ID
                LedgerAuditHelper.AccountInfo accountInfo = ledgerAuditHelper.getAccountInfo(posting.creditAccountId());
                if (accountInfo == null) {
                    log.warn("[BatchRun #{}] Interest {} is credited, but its owner could not be read; no notification sent",
                            batchRunId, posting.referenceNo());
                    continue;
                }
                notificationDispatcherHelper.dispatchInterestNotification(
                        accountInfo.customerId(),
                        posting.creditAccountId(),
                        accountInfo.accountNumber(),
                        posting.amount(),
                        posting.referenceNo()
                );
            }
            log.info("[BatchRun #{}] Month-end interest: {} credited, {} refused, totalAmount={}",
                    batchRunId, processed, refused, totalInterest);

            return BatchStepResult.builder()
                    .success(true)
                    .itemsProcessed(processed)
                    .totalAmount(totalInterest)
                    .message("Month-end interest capitalization credited across " + processed + " accounts"
                            + (refused > 0 ? "; " + refused + " refused by the ledger" : ""))
                    .build();

        } else {
            // =========================================================================
            // REGULAR DAY ACCRUAL (Daily GL liability booking, no cash payout)
            // =========================================================================
            try {
                for (Object[] account : savingsInterest(1, businessDate)) {
                    processed++;
                    totalInterest = totalInterest.add((BigDecimal) account[1]);
                }
            } catch (Exception ex) {
                log.warn("Could not query live ledger balances for daily interest accrual: {}", ex.getMessage());
            }

            log.info("[BatchRun #{}] Daily INTEREST_ACCRUAL completed: {} accounts accrued, totalAmount={}",
                    batchRunId, processed, totalInterest);
            return BatchStepResult.builder()
                    .success(true)
                    .itemsProcessed(processed)
                    .totalAmount(totalInterest)
                    .message("Daily interest accrued across " + processed + " accounts (Booked to GL 9002 Expense)")
                    .build();
        }
    }

    /**
     * The savings accounts that earn interest for the date, each with its interest for the given
     * number of days. Candidates come from the last audited balance; the ledger posts on the live
     * one. The audit store does not know what kind of account a balance belongs to, so the types
     * are read from the system of record: interest is for savings accounts only. Without that, a
     * loan account, whose "balance" is what the member still owes, was paid interest too.
     */
    private List<Object[]> savingsInterest(int days, LocalDate businessDate) {
        Map<Long, String> accountTypes = ledgerAuditHelper.accountTypes();
        List<Object[]> candidates = jdbcTemplate.query(
                "SELECT a.account_id, ROUND(a.new_balance * 0.02 * ? / 365.0, 2) AS interest " +
                "FROM audit_store.ledger_mutation_audit a " +
                "INNER JOIN (" +
                "    SELECT account_id, MAX(audit_id) AS max_id " +
                "    FROM audit_store.ledger_mutation_audit " +
                "    WHERE account_id < 9000 AND DATE(event_timestamp) <= ? " +
                "    GROUP BY account_id" +
                ") m ON a.audit_id = m.max_id " +
                "WHERE a.new_balance >= 1000",
                (rs, rowNum) -> new Object[]{rs.getLong(1), rs.getBigDecimal(2)},
                days,
                java.sql.Date.valueOf(businessDate)
        );
        return candidates.stream()
                .filter(candidate -> SAVINGS.equalsIgnoreCase(accountTypes.get((Long) candidate[0])))
                .toList();
    }
}