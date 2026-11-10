package com.group2.fse.batch_service.service;

import com.group2.fse.batch_service.dto.BatchRunDetailsResponseDto;
import com.group2.fse.batch_service.dto.EodTriggerRequestDto;
import com.group2.fse.batch_service.dto.EodTriggerResponseDto;

import java.util.List;

public interface BatchService {
    EodTriggerResponseDto triggerEod(EodTriggerRequestDto request, Long triggeredBy);
    BatchRunDetailsResponseDto getBatchRun(Long batchRunId);
    List<BatchRunDetailsResponseDto> listBatchRuns();
}
