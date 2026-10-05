package com.group2.fse.batch_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchStepResponseDto {
    private Integer stepNo;
    private String stepName;
    private String status;
    private Integer itemsProcessed;
    private String totalAmount;
    private Integer durationMs;
    private String message;
}
