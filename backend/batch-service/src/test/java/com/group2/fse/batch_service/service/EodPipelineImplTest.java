package com.group2.fse.batch_service.service;

import com.group2.fse.batch_service.entity.BatchRun;
import com.group2.fse.batch_service.entity.BatchStep;
import com.group2.fse.batch_service.repository.BatchRunRepository;
import com.group2.fse.batch_service.repository.BatchStepRepository;
import com.group2.fse.batch_service.service.impl.EodPipelineImpl;
import com.group2.fse.batch_service.step.BatchStepExecutor;
import com.group2.fse.batch_service.step.BatchStepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EodPipelineImpl Unit Tests")
class EodPipelineImplTest {

    @Mock
    private BatchRunRepository batchRunRepository;

    @Mock
    private BatchStepRepository batchStepRepository;

    @Mock
    private BatchStepExecutor step1Executor;

    @Mock
    private BatchStepExecutor step2Executor;

    @Mock
    private com.group2.fse.batch_service.event.BatchEventPublisher batchEventPublisher;

    private EodPipelineImpl eodPipeline;

    private BatchRun sampleRun;
    private BatchStep step1;
    private BatchStep step2;

    @BeforeEach
    void setUp() {
        lenient().when(step1Executor.getStepNo()).thenReturn(1);
        lenient().when(step2Executor.getStepNo()).thenReturn(2);

        eodPipeline = new EodPipelineImpl(
                batchRunRepository,
                batchStepRepository,
                List.of(step1Executor, step2Executor),
                batchEventPublisher
        );

        sampleRun = BatchRun.builder()
                .batchRunId(50L)
                .businessDate(LocalDate.of(2026, 10, 3))
                .status("RUNNING")
                .triggeredBy(1L)
                .startedAt(Instant.now())
                .build();

        step1 = BatchStep.builder()
                .batchStepId(501L)
                .batchRun(sampleRun)
                .stepNo(1)
                .stepName("INTEREST_ACCRUAL")
                .status("PENDING")
                .build();

        step2 = BatchStep.builder()
                .batchStepId(502L)
                .batchRun(sampleRun)
                .stepNo(2)
                .stepName("SERVICE_FEES")
                .status("PENDING")
                .build();
    }

    @Test
    @DisplayName("executePipeline runs all steps successfully and completes the run")
    void executePipeline_Success() {
        when(batchRunRepository.findById(50L)).thenReturn(Optional.of(sampleRun));
        when(batchStepRepository.findByBatchRun_BatchRunIdOrderByStepNoAsc(50L)).thenReturn(List.of(step1, step2));

        when(step1Executor.execute(eq(50L), any(LocalDate.class))).thenReturn(
                BatchStepResult.builder().success(true).itemsProcessed(3).totalAmount(new BigDecimal("12.24")).build()
        );
        when(step2Executor.execute(eq(50L), any(LocalDate.class))).thenReturn(
                BatchStepResult.builder().success(true).itemsProcessed(1).totalAmount(new BigDecimal("50.00")).build()
        );

        eodPipeline.executePipeline(50L, LocalDate.of(2026, 10, 3));

        assertThat(step1.getStatus()).isEqualTo("COMPLETED");
        assertThat(step1.getItemsProcessed()).isEqualTo(3);
        assertThat(step1.getDurationMs()).isNotNull();

        assertThat(step2.getStatus()).isEqualTo("COMPLETED");
        assertThat(step2.getItemsProcessed()).isEqualTo(1);
        assertThat(step2.getDurationMs()).isNotNull();

        // The report reads these back, so the step's own total is what is stored.
        assertThat(step1.totalAmountText()).isEqualTo("12.24");
        assertThat(step2.totalAmountText()).isEqualTo("50.00");

        assertThat(sampleRun.getStatus()).isEqualTo("COMPLETED");
        assertThat(sampleRun.getFinishedAt()).isNotNull();

        verify(batchRunRepository).saveAndFlush(sampleRun);
        verify(batchEventPublisher, times(2)).publishStepCompleted(any());
    }

    @Test
    @DisplayName("executePipeline closes the run as HALTED when something outside a step fails")
    void executePipeline_ClosesTheRunWhenTheLoopBreaks() {
        when(batchRunRepository.findById(50L)).thenReturn(Optional.of(sampleRun));
        when(batchStepRepository.findByBatchRun_BatchRunIdOrderByStepNoAsc(50L))
                .thenThrow(new IllegalStateException("audit store unreachable"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> eodPipeline.executePipeline(50L, LocalDate.of(2026, 10, 3)));

        // Left RUNNING, this run would refuse every later trigger.
        assertThat(sampleRun.getStatus()).isEqualTo("HALTED");
        verify(batchRunRepository).saveAndFlush(sampleRun);
    }

    @Test
    @DisplayName("At start-up a run still RUNNING is halted, its running step failed and the rest skipped")
    void startUp_HaltsARunCutOffByARestart() {
        step1.setStatus("RUNNING");
        when(batchRunRepository.findByStatus("RUNNING")).thenReturn(List.of(sampleRun));
        when(batchStepRepository.findByBatchRun_BatchRunIdOrderByStepNoAsc(50L)).thenReturn(List.of(step1, step2));

        eodPipeline.haltRunsCutOffByARestart();

        assertThat(sampleRun.getStatus()).isEqualTo("HALTED");
        assertThat(sampleRun.getFinishedAt()).isNotNull();
        assertThat(step1.getStatus()).isEqualTo("FAILED");
        assertThat(step2.getStatus()).isEqualTo("SKIPPED");
        verify(batchRunRepository).save(sampleRun);
    }

    @Test
    @DisplayName("executePipeline halts and marks subsequent steps as SKIPPED when a step fails")
    void executePipeline_HaltsOnFailure() {
        when(batchRunRepository.findById(50L)).thenReturn(Optional.of(sampleRun));
        when(batchStepRepository.findByBatchRun_BatchRunIdOrderByStepNoAsc(50L)).thenReturn(List.of(step1, step2));

        when(step1Executor.execute(eq(50L), any(LocalDate.class))).thenReturn(
                BatchStepResult.builder().success(false).message("Calculation error").build()
        );

        eodPipeline.executePipeline(50L, LocalDate.of(2026, 10, 3));

        assertThat(step1.getStatus()).isEqualTo("FAILED");
        assertThat(step2.getStatus()).isEqualTo("SKIPPED");

        assertThat(sampleRun.getStatus()).isEqualTo("HALTED");
        assertThat(sampleRun.getFinishedAt()).isNotNull();

        verify(step2Executor, never()).execute(any(), any());
        verify(batchRunRepository).saveAndFlush(sampleRun);
    }

    @Test
    @DisplayName("executePipeline returns early if BatchRun is not found after retry")
    void executePipeline_NotFound() {
        when(batchRunRepository.findById(999L)).thenReturn(Optional.empty());

        eodPipeline.executePipeline(999L, LocalDate.of(2026, 10, 3));

        verify(batchStepRepository, never()).findByBatchRun_BatchRunIdOrderByStepNoAsc(any());
        verify(step1Executor, never()).execute(any(), any());
    }
}
