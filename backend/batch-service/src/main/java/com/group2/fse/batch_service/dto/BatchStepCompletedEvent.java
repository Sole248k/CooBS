package com.group2.fse.batch_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Kafka event published to topic 'batch.eod.step.completed.v1'
 * per FUTURE_INTEGRATED_API_CONTRACT.md Section 13.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchStepCompletedEvent {
    private Long batchRunId;

    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate businessDate;

    private Integer stepNo;
    private String stepName;
    private String status;
    private Integer itemsProcessed;
    private String totalAmount;
    private Integer durationMs;
    private String message;
}
