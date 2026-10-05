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
public class HoldResponseDto {
    private String holdId;
    private Long accountId;
    private BigDecimal amount;
    private String reason;
    private String status;
    private Instant createdAt;
}
