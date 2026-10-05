package com.group2.fse.batch_service.service.impl;

import com.group2.fse.batch_service.entity.BatchRun;
import com.group2.fse.batch_service.entity.BatchStep;
import com.group2.fse.batch_service.repository.BatchRunRepository;
import com.group2.fse.batch_service.repository.BatchStepRepository;
import com.group2.fse.batch_service.service.EodPipeline;
import com.group2.fse.batch_service.step.BatchStepExecutor;
import com.group2.fse.batch_service.step.BatchStepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asynchronous runner that coordinates the 6-step EOD Batch execution.
 * Completes in approximately 5 to 8 seconds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodPipelineImpl implements EodPipeline {

    private final BatchRunRepository batchRunRepository;
    private final BatchStepRepository batchStepRepository;
    private final List<BatchStepExecutor> stepExecutors;

    @Async
    @Override
    public void executePipeline(Long batchRunId, LocalDate businessDate) {
        log.info("[BatchRun #{}] Starting asynchronous EOD pipeline execution for businessDate={}", batchRunId, businessDate);

        BatchRun run = null;
        for (int i = 0; i < 5; i++) {
            run = batchRunRepository.findById(batchRunId).orElse(null);
            if (run != null) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (run == null) {
            log.error("[BatchRun #{}] Cannot execute pipeline: BatchRun entity not found after retry", batchRunId);
            return;
        }

        Map<Integer, BatchStepExecutor> executorMap = stepExecutors.stream()
                .collect(Collectors.toMap(BatchStepExecutor::getStepNo, Function.identity()));

        List<BatchStep> steps = batchStepRepository.findByBatchRun_BatchRunIdOrderByStepNoAsc(batchRunId);

        boolean pipelineHalted = false;

        for (BatchStep step : steps) {
            int stepNo = step.getStepNo();
            BatchStepExecutor executor = executorMap.get(stepNo);

            if (pipelineHalted) {
                step.setStatus("SKIPPED");
                batchStepRepository.saveAndFlush(step);
                log.info("[BatchRun #{}] Step {} ({}) SKIPPED due to prior failure", batchRunId, stepNo, step.getStepName());
                continue;
            }

            if (executor == null) {
                log.warn("[BatchRun #{}] No executor found for step {}", batchRunId, stepNo);
                continue;
            }

            long startTime = System.currentTimeMillis();
            step.setStatus("RUNNING");
            batchStepRepository.saveAndFlush(step);

            try {
                BatchStepResult result = executor.execute(batchRunId, businessDate);
                long duration = System.currentTimeMillis() - startTime;
                step.setDurationMs((int) duration);

                if (result != null && result.isSuccess()) {
                    step.setStatus("COMPLETED");
                    step.setItemsProcessed(result.getItemsProcessed());
                } else {
                    step.setStatus("FAILED");
                    pipelineHalted = true;
                }
            } catch (Exception ex) {
                long duration = System.currentTimeMillis() - startTime;
                step.setDurationMs((int) duration);
                step.setStatus("FAILED");
                pipelineHalted = true;
                log.error("[BatchRun #{}] Step {} ({}) failed with exception: {}", batchRunId, stepNo, step.getStepName(), ex.getMessage(), ex);
            }

            batchStepRepository.saveAndFlush(step);
        }

        BatchRun finalRun = batchRunRepository.findById(batchRunId).orElse(run);
        finalRun.setFinishedAt(Instant.now());
        if (pipelineHalted) {
            finalRun.setStatus("HALTED");
            log.warn("[BatchRun #{}] EOD pipeline HALTED due to step failure", batchRunId);
        } else {
            finalRun.setStatus("COMPLETED");
            log.info("[BatchRun #{}] EOD pipeline successfully COMPLETED in {}ms",
                    batchRunId, (finalRun.getFinishedAt().toEpochMilli() - finalRun.getStartedAt().toEpochMilli()));
        }

        batchRunRepository.saveAndFlush(finalRun);
    }
}
