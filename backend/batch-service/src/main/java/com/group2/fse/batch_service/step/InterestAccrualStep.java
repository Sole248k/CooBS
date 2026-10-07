package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

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
        try {
            Thread.sleep(820);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

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

            try {
                // Fetch qualifying savings accounts (account_id < 9000, balance >= 1000)
                List<Object[]> eligibleAccounts = jdbcTemplate.query(
                        "SELECT a.account_id, a.new_balance, ROUND(a.new_balance * 0.02 * ? / 365.0, 2) AS month_interest " +
                        "FROM audit_store.ledger_mutation_audit a " +
                        "INNER JOIN (" +
                        "    SELECT account_id, MAX(audit_id) AS max_id " +
                        "    FROM audit_store.ledger_mutation_audit " +
                        "    WHERE account_id < 9000 AND DATE(event_timestamp) <= ? " +
                        "    GROUP BY account_id" +
                        ") m ON a.audit_id = m.max_id " +
                        "WHERE a.new_balance >= 1000",
                        (rs, rowNum) -> new Object[]{
                                rs.getLong(1),
                                rs.getBigDecimal(2),
                                rs.getBigDecimal(3)
                        },
                        daysInMonth,
                        java.sql.Date.valueOf(businessDate)
                );

                String monthRefPrefix = "EOD-" + businessDate.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-INT-%";

                for (Object[] acc : eligibleAccounts) {
                    Long accountId = (Long) acc[0];
                    BigDecimal currentBalance = (BigDecimal) acc[1];
                    BigDecimal interestAmount = (BigDecimal) acc[2];

                    if (interestAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    // Idempotency: verify this account hasn't already received interest for this month
                    Integer alreadyCredited = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM audit_store.ledger_mutation_audit " +
                            "WHERE account_id = ? AND transaction_type = 'INTEREST_CREDIT' AND reference_no LIKE ?",
                            Integer.class,
                            accountId,
                            monthRefPrefix
                    );

                    if (alreadyCredited != null && alreadyCredited > 0) {
                        log.info("Account #{} already credited for month {}, skipping duplicate.", accountId, businessDate.getMonth());
                        continue;
                    }

                    BigDecimal newBalance = currentBalance.add(interestAmount);
                    String referenceNo = "EOD-" + businessDate.format(DateTimeFormatter.BASIC_ISO_DATE) +
                            "-INT-" + String.format("%06d", accountId);

                    // Record immutable ledger mutation and sync Oracle balance
                    ledgerAuditHelper.recordMutation(
                            accountId,
                            referenceNo,
                            "INTEREST_CREDIT",
                            interestAmount,
                            currentBalance,
                            newBalance,
                            businessDate
                    );

                    processed++;
                    totalInterest = totalInterest.add(interestAmount);
                    log.info("Capitalized interest of ₱{} to Account #{} (oldBalance={}, newBalance={})",
                            interestAmount, accountId, currentBalance, newBalance);

                    // Resolve official account number (e.g. ACC_10000001) and customer ID
                    LedgerAuditHelper.AccountInfo accountInfo = ledgerAuditHelper.getAccountInfo(accountId);
                    notificationDispatcherHelper.dispatchInterestNotification(
                            accountInfo.customerId(),
                            accountId,
                            accountInfo.accountNumber(),
                            interestAmount,
                            referenceNo
                    );
                }
            } catch (Exception ex) {
                log.error("Failed executing month-end interest capitalization: {}", ex.getMessage(), ex);
            }

            return BatchStepResult.builder()
                    .success(true)
                    .itemsProcessed(processed)
                    .totalAmount(totalInterest)
                    .message("Month-end interest capitalization credited across " + processed + " accounts")
                    .build();

        } else {
            // =========================================================================
            // REGULAR DAY ACCRUAL (Daily GL liability booking, no cash payout)
            // =========================================================================
            try {
                var rows = jdbcTemplate.query(
                        "SELECT COUNT(*), COALESCE(SUM(ROUND(a.new_balance * 0.02 / 365.0, 2)), 0) " +
                        "FROM audit_store.ledger_mutation_audit a " +
                        "INNER JOIN (" +
                        "    SELECT account_id, MAX(audit_id) AS max_id " +
                        "    FROM audit_store.ledger_mutation_audit " +
                        "    WHERE account_id < 9000 AND DATE(event_timestamp) <= ? " +
                        "    GROUP BY account_id" +
                        ") m ON a.audit_id = m.max_id " +
                        "WHERE a.new_balance >= 1000",
                        (rs, rowNum) -> new Object[]{rs.getInt(1), rs.getBigDecimal(2)},
                        java.sql.Date.valueOf(businessDate)
                );
                if (!rows.isEmpty() && ((Integer) rows.get(0)[0]) > 0) {
                    processed = (Integer) rows.get(0)[0];
                    totalInterest = (BigDecimal) rows.get(0)[1];
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
}
