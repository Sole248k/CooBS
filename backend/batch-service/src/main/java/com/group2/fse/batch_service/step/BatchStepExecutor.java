package com.group2.fse.batch_service.step;

import java.time.LocalDate;

/**
 * Common contract for each of the 6 EOD batch steps.
 */
public interface BatchStepExecutor {
    int getStepNo();
    String getStepName();
    BatchStepResult execute(Long batchRunId, LocalDate businessDate);
}
