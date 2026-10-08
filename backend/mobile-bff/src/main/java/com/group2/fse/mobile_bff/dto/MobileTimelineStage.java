package com.group2.fse.mobile_bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileTimelineStage {

    private Integer stageNumber;
    private Integer stageNo;
    private String stageKey;
    private String stageName;
    private String title;
    private String subtitle;
    private String service;
    private String status;
    private String outcome;
    private String timestamp;
    private String occurredAt;
    private String details;
}
