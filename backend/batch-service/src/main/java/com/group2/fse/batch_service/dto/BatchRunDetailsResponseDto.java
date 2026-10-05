package com.group2.fse.batch_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchRunDetailsResponseDto {
    private Long batchRunId;
    private LocalDate businessDate;
    private String status;
    private Long triggeredBy;
    private Instant startedAt;
    private Instant finishedAt;
    private List<BatchStepResponseDto> steps;
}
