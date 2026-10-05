package com.group2.fse.audit_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Contract U4: Daily Audit Seal Response DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailySealResponseDto {
    private LocalDate businessDate;
    private Long lastAuditId;
    private String sealHash;
    private Instant sealedAt;
    private Long batchRunId;
}
