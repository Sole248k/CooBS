package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 2: SERVICE_FEES
 * Charges the monthly maintenance fee to accounts under ₱500.00: debit the customer, credit
 * 9003 GL_FEE_INCOME. The posting goes through the ledger (L8), which checks the funds that are
 * really available, so an account whose money is on hold is refused there and left alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceFeesStep implements BatchStepExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final LedgerPostings ledgerPostings;

    private static final BigDecimal FEE_AMOUNT = new BigDecimal("50.00");
    private static final Long GL_FEE_INCOME = 9003L;

    @Override
    public int getStepNo() {
        return 2;
    }

    @Override
    public String getStepName() {
        return "SERVICE_FEES";
    }

    @Override
    public BatchStepResult execute(Long batchRunId, LocalDate businessDate) {
        log.info("[BatchRun #{}] Executing Step 2: SERVICE_FEES for businessDate={}", batchRunId, businessDate);

        // Candidates come from the last audited balance. It only chooses who is asked; the
        // ledger decides, on the live balance, whether the fee can be taken.
        List<Object[]> lowBalanceAccounts = jdbcTemplate.query(
                "SELECT a.account_id, a.new_balance " +
                "FROM audit_store.ledger_mutation_audit a " +
                "INNER JOIN (" +
                "    SELECT account_id, MAX(audit_id) AS max_id " +
                "    FROM audit_store.ledger_mutation_audit " +
                "    WHERE account_id < 9000 AND DATE(event_timestamp) <= ? " +
                "    GROUP BY account_id" +
                ") m ON a.audit_id = m.max_id " +
                "WHERE a.new_balance < 500 AND a.new_balance > 0",
                (rs, rowNum) -> new Object[]{rs.getLong(1), rs.getBigDecimal(2)},
                java.sql.Date.valueOf(businessDate)
        );

        // One reference per account and month: a second run in the same month is answered from
        // the journal and charges nothing more.
        String month = businessDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<LedgerPostings.Posting> postings = new ArrayList<>();
        for (Object[] row : lowBalanceAccounts) {
            Long accountId = (Long) row[0];
            BigDecimal balance = (BigDecimal) row[1];
            BigDecimal fee = balance.compareTo(FEE_AMOUNT) >= 0 ? FEE_AMOUNT : balance;
            postings.add(new LedgerPostings.Posting("EOD-" + month + "-FEE-" + String.format("%06d", accountId),
                    accountId, GL_FEE_INCOME, fee, "Monthly maintenance fee"));
        }

        Map<String, String> outcomes = ledgerPostings.post(batchRunId, getStepName(), postings);

        int charged = 0;
        int refused = 0;
        BigDecimal totalFees = BigDecimal.ZERO;
        for (LedgerPostings.Posting posting : postings) {
            String errorCode = outcomes.get(posting.referenceNo());
            if (errorCode == null) {
                charged++;
                totalFees = totalFees.add(posting.amount());
            } else if (!LedgerPostings.ALREADY_USED.equals(errorCode)) {
                // Already charged this month for another amount is not a failure; anything else is.
                refused++;
                log.warn("[BatchRun #{}] Fee {} for account #{} refused by the ledger: {}",
                        batchRunId, posting.referenceNo(), posting.debitAccountId(), errorCode);
            }
        }

        String message = charged == 0 && refused == 0
                ? "No accounts subject to service fees"
                : "Maintenance fee charged to " + charged + " low-balance accounts (credited GL 9003)"
                        + (refused > 0 ? "; " + refused + " refused by the ledger" : "");
        log.info("[BatchRun #{}] SERVICE_FEES completed: {} charged, {} refused, totalAmount={}",
                batchRunId, charged, refused, totalFees);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(charged)
                .totalAmount(totalFees)
                .message(message)
                .build();
    }
}
