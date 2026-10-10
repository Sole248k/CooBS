package com.group2.fse.batch_service.service.impl;

import com.group2.fse.batch_service.entity.BatchRun;
import com.group2.fse.batch_service.entity.BatchStep;
import com.group2.fse.batch_service.repository.BatchRunRepository;
import com.group2.fse.batch_service.repository.BatchStepRepository;
import com.group2.fse.batch_service.service.EodPipeline;
import com.group2.fse.batch_service.dto.BatchStepCompletedEvent;
import com.group2.fse.batch_service.event.BatchEventPublisher;
import com.group2.fse.batch_service.step.BatchStepExecutor;
import com.group2.fse.batch_service.step.BatchStepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asynchronous runner that coordinates the 6-step EOD Batch execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodPipelineImpl implements EodPipeline {

    private static final int MESSAGE_MAX_LENGTH = 255;

    private final BatchRunRepository batchRunRepository;
    private final BatchStepRepository batchStepRepository;
    private final List<BatchStepExecutor> stepExecutors;
    private final BatchEventPublisher batchEventPublisher;

    /**
     * A run still RUNNING at start-up was cut off by a restart: nothing is executing it, and
     * left as it is it would refuse every later trigger. Its postings are safe to send again,
     * because the ledger recognises each one by its reference.
     *
     * shortcut: one instance. A second copy starting up would halt the first one's live run;
     * give the run a lease before running two.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void haltRunsCutOffByARestart() {
        for (BatchRun run : batchRunRepository.findByStatus("RUNNING")) {
            log.warn("[BatchRun #{}] was RUNNING when the service started; marked HALTED", run.getBatchRunId());
            for (BatchStep step : batchStepRepository.findByBatchRun_BatchRunIdOrderByStepNoAsc(run.getBatchRunId())) {
                if ("RUNNING".equals(step.getStatus())) {
                    step.setStatus("FAILED");
                    step.setMessage("Interrupted: the service restarted");
                    batchStepRepository.save(step);
                } else if ("PENDING".equals(step.getStatus())) {
                    step.setStatus("SKIPPED");
                    step.setMessage("Not run: the service restarted");
                    batchStepRepository.save(step);
                }
            }
            run.setStatus("HALTED");
            run.setFinishedAt(Instant.now());
            batchRunRepository.save(run);
        }
    }

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

        boolean pipelineHalted = false;
        boolean reachedTheEnd = false;
        try {
            Map<Integer, BatchStepExecutor> executorMap = stepExecutors.stream()
                    .collect(Collectors.toMap(BatchStepExecutor::getStepNo, Function.identity()));

            List<BatchStep> steps = batchStepRepository.findByBatchRun_BatchRunIdOrderByStepNoAsc(batchRunId);

            for (BatchStep step : steps) {
                int stepNo = step.getStepNo();
                BatchStepExecutor executor = executorMap.get(stepNo);

                if (pipelineHalted) {
                    step.setStatus("SKIPPED");
                    step.setMessage("Not run: an earlier step failed");
                    batchStepRepository.saveAndFlush(step);
                    log.info("[BatchRun #{}] Step {} ({}) SKIPPED due to prior failure", batchRunId, stepNo, step.getStepName());
                    publishStepEvent(batchRunId, businessDate, step);
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

                    // The report shows what the step said, so both are kept whether it passed or not.
                    if (result != null) {
                        step.setTotalAmount(result.getTotalAmount());
                        step.setMessage(cut(result.getMessage()));
                    }
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
                    step.setMessage(cut("Failed: " + ex.getMessage()));
                    pipelineHalted = true;
                    log.error("[BatchRun #{}] Step {} ({}) failed with exception: {}", batchRunId, stepNo, step.getStepName(), ex.getMessage(), ex);
                }

                batchStepRepository.saveAndFlush(step);
                publishStepEvent(batchRunId, businessDate, step);
            }
            reachedTheEnd = true;
        } finally {
            // Whatever stopped the loop, the run is closed: one left RUNNING refuses every later trigger.
            BatchRun finalRun = batchRunRepository.findById(batchRunId).orElse(run);
            finalRun.setFinishedAt(Instant.now());
            if (pipelineHalted || !reachedTheEnd) {
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

    private void publishStepEvent(Long batchRunId, LocalDate businessDate, BatchStep step) {
        BatchStepCompletedEvent event = BatchStepCompletedEvent.builder()
                .batchRunId(batchRunId)
                .businessDate(businessDate)
                .stepNo(step.getStepNo())
                .stepName(step.getStepName())
                .status(step.getStatus())
                .itemsProcessed(step.getItemsProcessed())
                .totalAmount(step.totalAmountText())
                .durationMs(step.getDurationMs())
                .message(step.getMessage())
                .build();

        try {
            batchEventPublisher.publishStepCompleted(event);
        } catch (Exception ex) {
            log.warn("[BatchRun #{}] Error dispatching step completed event to publisher: {}", batchRunId, ex.getMessage());
        }
    }

    private static String cut(String message) {
        return message != null && message.length() > MESSAGE_MAX_LENGTH ? message.substring(0, MESSAGE_MAX_LENGTH) : message;
    }
}
