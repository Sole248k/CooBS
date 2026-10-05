package com.group2.fse.batch_service.service.impl;

import com.group2.fse.batch_service.dto.BatchRunDetailsResponseDto;
import com.group2.fse.batch_service.dto.BatchStepResponseDto;
import com.group2.fse.batch_service.dto.EodTriggerRequestDto;
import com.group2.fse.batch_service.dto.EodTriggerResponseDto;
import com.group2.fse.batch_service.entity.BatchRun;
import com.group2.fse.batch_service.entity.BatchStep;
import com.group2.fse.batch_service.exception.BatchAlreadyRunningException;
import com.group2.fse.batch_service.exception.ResourceNotFoundException;
import com.group2.fse.batch_service.repository.BatchRunRepository;
import com.group2.fse.batch_service.repository.BatchStepRepository;
import com.group2.fse.batch_service.service.BatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    private final BatchRunRepository batchRunRepository;
    private final BatchStepRepository batchStepRepository;

    private static final String[] STEP_NAMES = {
            "INTEREST_ACCRUAL",
            "SERVICE_FEES",
            "STANDING_INSTRUCTIONS",
            "LOAN_AMORTIZATION",
            "GL_RECONCILIATION",
            "AUDIT_SEAL"
    };

    @Override
    @Transactional
    public EodTriggerResponseDto triggerEod(EodTriggerRequestDto request, Long triggeredBy) {
        log.info("EOD batch trigger requested by userId={}", triggeredBy);

        if (batchRunRepository.existsByStatus("RUNNING")) {
            log.warn("EOD trigger rejected: A batch run is already in progress");
            throw new BatchAlreadyRunningException("A batch run is currently in progress");
        }

        LocalDate businessDate = (request != null && request.getBusinessDate() != null)
                ? request.getBusinessDate()
                : LocalDate.now();

        BatchRun batchRun = BatchRun.builder()
                .businessDate(businessDate)
                .triggeredBy(triggeredBy != null ? triggeredBy : 1L)
                .status("RUNNING")
                .startedAt(Instant.now())
                .steps(new ArrayList<>())
                .build();

        BatchRun savedRun = batchRunRepository.save(batchRun);

        // Initialize the 6 steps per contract
        for (int i = 0; i < STEP_NAMES.length; i++) {
            BatchStep step = BatchStep.builder()
                    .batchRun(savedRun)
                    .stepNo(i + 1)
                    .stepName(STEP_NAMES[i])
                    .status("PENDING")
                    .itemsProcessed(0)
                    .durationMs(null)
                    .build();
            savedRun.getSteps().add(step);
        }

        batchRunRepository.save(savedRun);
        log.info("EOD batch run initialized with ID={}", savedRun.getBatchRunId());

        return EodTriggerResponseDto.builder()
                .batchRunId(savedRun.getBatchRunId())
                .businessDate(savedRun.getBusinessDate())
                .status(savedRun.getStatus())
                .startedAt(savedRun.getStartedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BatchRunDetailsResponseDto getBatchRun(Long batchRunId) {
        log.info("Fetching details for batchRunId={}", batchRunId);
        BatchRun run = batchRunRepository.findById(batchRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch run not found with ID: " + batchRunId));

        return mapToDetailsDto(run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchRunDetailsResponseDto> listBatchRuns() {
        log.info("Listing all batch runs");
        return batchRunRepository.findAllByOrderByBatchRunIdDesc()
                .stream()
                .map(this::mapToDetailsDto)
                .toList();
    }

    private BatchRunDetailsResponseDto mapToDetailsDto(BatchRun run) {
        List<BatchStepResponseDto> stepDtos = run.getSteps().stream()
                .map(s -> BatchStepResponseDto.builder()
                        .stepNo(s.getStepNo())
                        .stepName(s.getStepName())
                        .status(s.getStatus())
                        .itemsProcessed(s.getItemsProcessed())
                        .durationMs(s.getDurationMs())
                        .build())
                .toList();

        return BatchRunDetailsResponseDto.builder()
                .batchRunId(run.getBatchRunId())
                .businessDate(run.getBusinessDate())
                .status(run.getStatus())
                .triggeredBy(run.getTriggeredBy())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .steps(stepDtos)
                .build();
    }
}
