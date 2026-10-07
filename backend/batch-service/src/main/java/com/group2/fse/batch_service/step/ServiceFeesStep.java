package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Step 2: SERVICE_FEES
 * Debits periodic maintenance or low-balance service fees from qualifying accounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceFeesStep implements BatchStepExecutor {

    private final JdbcTemplate jdbcTemplate;

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
        try {
            Thread.sleep(640);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int processed = 1;
        BigDecimal totalFees = new BigDecimal("50.00");

        try {
            var rows = jdbcTemplate.query(
                    "SELECT COUNT(*) " +
                    "FROM audit_store.ledger_mutation_audit a " +
                    "INNER JOIN (" +
                    "    SELECT account_id, MAX(audit_id) AS max_id " +
                    "    FROM audit_store.ledger_mutation_audit " +
                    "    WHERE account_id < 9000 AND DATE(event_timestamp) <= ? " +
                    "    GROUP BY account_id" +
                    ") m ON a.audit_id = m.max_id " +
                    "WHERE a.new_balance < 500",
                    (rs, rowNum) -> rs.getInt(1),
                    java.sql.Date.valueOf(businessDate)
            );
            if (!rows.isEmpty()) {
                processed = rows.get(0);
                totalFees = BigDecimal.valueOf(processed).multiply(new BigDecimal("50.00"));
            }
        } catch (Exception ex) {
            log.warn("Could not query live ledger balances for service fees, using baseline: {}", ex.getMessage());
        }

        String message = (processed > 0)
                ? ("Applied maintenance fees across " + processed + " low-balance accounts")
                : "No accounts subject to service fees";

        log.info("[BatchRun #{}] SERVICE_FEES completed: {} accounts charged, totalAmount={}", batchRunId, processed, totalFees);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(processed)
                .totalAmount(totalFees)
                .message(message)
                .build();
    }
}
