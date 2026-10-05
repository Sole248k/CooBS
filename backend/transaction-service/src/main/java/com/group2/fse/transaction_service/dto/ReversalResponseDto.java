package com.group2.fse.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalResponseDto {
    private String reversalId;
    private String originalReferenceNo;
    private BigDecimal amount;
    private String status;
    private Instant timestamp;
}
