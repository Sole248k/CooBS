package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Step 5: GL_RECONCILIATION
 * Compares the day's total debits with its total credits in the journal. Every posting with
 * two legs has to balance. Cash paid in or out at the counter is one leg only, so it is left
 * out of the comparison and reported beside it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlReconciliationStep implements BatchStepExecutor {

    private final LedgerAuditHelper ledgerAuditHelper;

    @Override
    public int getStepNo() {
        return 5;
    }

    @Override
    public String getStepName() {
        return "GL_RECONCILIATION";
    }

    @Override
    public BatchStepResult execute(Long batchRunId, LocalDate businessDate) {
        log.info("[BatchRun #{}] Executing Step 5: GL_RECONCILIATION for businessDate={}", batchRunId, businessDate);

        LedgerAuditHelper.JournalTotals day = ledgerAuditHelper.journalTotals(businessDate);
        BigDecimal variance = day.debits().subtract(day.credits()).abs();

        if (variance.signum() != 0) {
            log.error("[BatchRun #{}] GL_RECONCILIATION variance: debits={}, credits={}", batchRunId, day.debits(), day.credits());
            return BatchStepResult.builder()
                    .success(false)
                    .itemsProcessed(day.entries())
                    .totalAmount(variance)
                    .message("Variance detected: debits " + money(day.debits()) + ", credits " + money(day.credits()))
                    .build();
        }

        boolean counterCash = day.cashIn().signum() != 0 || day.cashOut().signum() != 0;
        String message = "Debits equal credits (" + day.entries() + " journal entries"
                + (counterCash ? "; counter cash in " + money(day.cashIn()) + ", out " + money(day.cashOut()) : "") + ")";
        log.info("[BatchRun #{}] GL_RECONCILIATION verified: {}", batchRunId, message);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(day.entries())
                .totalAmount(new BigDecimal("0.00"))
                .message(message)
                .build();
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
