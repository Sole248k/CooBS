package com.group2.fse.mobile_bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileTimelineResponse {

    private String transactionId;
    private String referenceNo;
    private String trackingReference;
    private String status;
    private String overallStatus;
    private String view;
    private Integer currentStage;
    private Integer totalStages;
    @Builder.Default
    private List<MobileTimelineStage> stages = new ArrayList<>();
    private String failureMessage;
}
