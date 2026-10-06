package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Execution Status DTO for GET /api/v1/transactions/{trackingRef}/status
 * Exposing sub-second execution states: Ingress -> Screened -> Committed -> Notified.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionStatusResponseDto {

    private UUID transactionId;
    private String trackingReference;
    private String status;
    private String overallStatus;
    private String currentExecutionState;
    @Builder.Default
    private String executionFlow = "Ingress -> Screened -> Committed -> Notified";
    private List<ExecutionStateDto> executionStates;
    private List<TimelineStageDto> detailedStages;
    private String failureMessage;
    private String timelineUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExecutionStateDto {
        private String name;
        private String stateKey;
        private String status;
        private String outcome;
        private String description;
        private List<Integer> stages;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant occurredAt;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant completedAt;

        private Long durationMs;
    }
}
