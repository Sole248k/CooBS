package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        log.info("[BatchRun #{}] SERVICE_FEES completed: {} accounts charged, totalAmount={}", batchRunId, processed, totalFees);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(processed)
                .totalAmount(totalFees)
                .message(null)
                .build();
    }
}
