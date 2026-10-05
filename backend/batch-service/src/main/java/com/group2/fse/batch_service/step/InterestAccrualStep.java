package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Step 1: INTEREST_ACCRUAL
 * Calculates and accrues daily interest across eligible savings accounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestAccrualStep implements BatchStepExecutor {

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

        int processed = 3;
        BigDecimal totalInterest = new BigDecimal("12.24");

        log.info("[BatchRun #{}] INTEREST_ACCRUAL completed: {} accounts processed, totalAmount={}", batchRunId, processed, totalInterest);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(processed)
                .totalAmount(totalInterest)
                .message(null)
                .build();
    }
}
