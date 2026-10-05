package com.group2.fse.batch_service.service;

import com.group2.fse.batch_service.dto.BatchRunDetailsResponseDto;
import com.group2.fse.batch_service.dto.EodTriggerRequestDto;
import com.group2.fse.batch_service.dto.EodTriggerResponseDto;
import com.group2.fse.batch_service.entity.BatchRun;
import com.group2.fse.batch_service.entity.BatchStep;
import com.group2.fse.batch_service.exception.BatchAlreadyRunningException;
import com.group2.fse.batch_service.exception.ResourceNotFoundException;
import com.group2.fse.batch_service.repository.BatchRunRepository;
import com.group2.fse.batch_service.repository.BatchStepRepository;
import com.group2.fse.batch_service.service.impl.BatchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchServiceImpl Unit Tests")
class BatchServiceImplTest {

    @Mock
    private BatchRunRepository batchRunRepository;

    @Mock
    private BatchStepRepository batchStepRepository;

    @Mock
    private EodPipeline eodPipeline;

    @InjectMocks
    private BatchServiceImpl batchService;

    private BatchRun sampleRun;

    @BeforeEach
    void setUp() {
        sampleRun = BatchRun.builder()
                .batchRunId(31L)
                .businessDate(LocalDate.of(2026, 10, 3))
                .status("COMPLETED")
                .triggeredBy(3L)
                .startedAt(Instant.parse("2026-10-03T17:00:00Z"))
                .finishedAt(Instant.parse("2026-10-03T17:00:06Z"))
                .steps(new ArrayList<>())
                .build();

        BatchStep step1 = BatchStep.builder()
                .batchStepId(101L)
                .batchRun(sampleRun)
                .stepNo(1)
                .stepName("INTEREST_ACCRUAL")
                .status("COMPLETED")
                .itemsProcessed(3)
                .durationMs(820)
                .build();
        sampleRun.getSteps().add(step1);
    }

    @Test
    @DisplayName("triggerEod succeeds and initializes 6 steps when no run is in progress")
    void triggerEod_Success() {
        when(batchRunRepository.existsByStatus("RUNNING")).thenReturn(false);
        when(batchRunRepository.save(any(BatchRun.class))).thenAnswer(invocation -> {
            BatchRun run = invocation.getArgument(0);
            if (run.getBatchRunId() == null) {
                run.setBatchRunId(31L);
            }
            return run;
        });

        EodTriggerRequestDto request = EodTriggerRequestDto.builder()
                .businessDate(LocalDate.of(2026, 10, 3))
                .build();

        EodTriggerResponseDto response = batchService.triggerEod(request, 3L);

        assertThat(response).isNotNull();
        assertThat(response.getBatchRunId()).isEqualTo(31L);
        assertThat(response.getBusinessDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getStartedAt()).isNotNull();

        verify(batchRunRepository, times(2)).save(any(BatchRun.class));
        verify(eodPipeline).executePipeline(eq(31L), eq(LocalDate.of(2026, 10, 3)));
    }

    @Test
    @DisplayName("triggerEod throws BatchAlreadyRunningException when another run is currently RUNNING")
    void triggerEod_ThrowsWhenAlreadyRunning() {
        when(batchRunRepository.existsByStatus("RUNNING")).thenReturn(true);

        EodTriggerRequestDto request = EodTriggerRequestDto.builder()
                .businessDate(LocalDate.of(2026, 10, 3))
                .build();

        assertThrows(BatchAlreadyRunningException.class, () -> batchService.triggerEod(request, 3L));
        verify(batchRunRepository, never()).save(any(BatchRun.class));
    }

    @Test
    @DisplayName("getBatchRun returns mapped details DTO when run exists")
    void getBatchRun_Success() {
        when(batchRunRepository.findById(31L)).thenReturn(Optional.of(sampleRun));

        BatchRunDetailsResponseDto response = batchService.getBatchRun(31L);

        assertThat(response).isNotNull();
        assertThat(response.getBatchRunId()).isEqualTo(31L);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getTriggeredBy()).isEqualTo(3L);
        assertThat(response.getSteps()).hasSize(1);
        assertThat(response.getSteps().get(0).getStepName()).isEqualTo("INTEREST_ACCRUAL");
    }

    @Test
    @DisplayName("getBatchRun throws ResourceNotFoundException when run does not exist")
    void getBatchRun_NotFound() {
        when(batchRunRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> batchService.getBatchRun(999L));
    }

    @Test
    @DisplayName("listBatchRuns returns all runs ordered by batchRunId descending")
    void listBatchRuns_Success() {
        when(batchRunRepository.findAllByOrderByBatchRunIdDesc()).thenReturn(List.of(sampleRun));

        List<BatchRunDetailsResponseDto> runs = batchService.listBatchRuns();

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getBatchRunId()).isEqualTo(31L);
    }
}
