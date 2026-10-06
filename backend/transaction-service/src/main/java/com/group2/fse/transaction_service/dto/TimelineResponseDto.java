package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineResponseDto {

    private UUID transactionId;
    private String trackingReference;
    private String status;
    private String overallStatus;
    private String view;
    private Integer currentStage;
    private Integer totalStages;
    private List<TimelineStageDto> stages;
    private String failureMessage;
}
