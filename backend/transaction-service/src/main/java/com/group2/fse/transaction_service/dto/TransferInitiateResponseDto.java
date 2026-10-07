package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /** A string on the wire; set it through Money.toContractScale so it has two to four decimals. */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;
    private String currency;

    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant createdAt;

    private String timelineUrl;

    /** True when this is the stored answer to a repeated Idempotency-Key. Decides 200 or 202; not sent. */
    @JsonIgnore
    private boolean replayed;
}
