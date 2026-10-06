package com.group2.fse.batch_service.service;

import java.time.LocalDate;

/**
 * Contract for the asynchronous End-of-Day Batch Pipeline runner.
 */
public interface EodPipeline {
    void executePipeline(Long batchRunId, LocalDate businessDate);
}
