package com.group2.fse.batch_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EodTriggerResponseDto {
    private Long batchRunId;
    private LocalDate businessDate;
    private String status;
    private Instant startedAt;
}
