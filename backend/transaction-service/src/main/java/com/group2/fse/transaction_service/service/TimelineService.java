package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto;
import com.group2.fse.transaction_service.dto.TimelineResponseDto;
import com.group2.fse.transaction_service.dto.TransactionDetailResponseDto;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

public interface TimelineService {

    TimelineResponseDto getTimeline(String identifier, String requestedView, Authentication authentication);

    ExecutionStatusResponseDto getExecutionStatus(String identifier);

    TransactionDetailResponseDto getTransactionDetail(String identifier);

    void recordStage(UUID transactionId, int stageNo, String stageName, String outcome,
                     String detail, Instant occurredAt, String transactionStatus, String failureCode);
}
