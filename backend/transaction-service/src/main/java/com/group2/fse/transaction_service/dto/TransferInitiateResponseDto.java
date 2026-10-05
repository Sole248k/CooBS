package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immediate 202 Accepted response payload for POST /api/v1/transactions/transfers (T1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferInitiateResponseDto {

    private String transactionId;
    private String referenceNo;
    private String status;
    private BigDecimal amount;
    private String currency;

    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant createdAt;

    private String timelineUrl;
}
