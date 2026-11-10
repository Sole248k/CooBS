package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineStageDto {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer stageNo;

    private Integer stageNumber;
    private String stageKey;
    private String stageName;
    private String title;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String subtitle;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String service;

    private String status;
    private String outcome;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private String detail;
}
