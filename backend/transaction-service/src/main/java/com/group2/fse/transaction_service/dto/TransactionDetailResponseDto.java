package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetailResponseDto {

    private UUID transactionId;
    private String referenceNo;
    private String channel;
    private Long sourceAccountId;
    private String sourceAccountNumber;
    private Long destinationAccountId;
    private String destinationAccountNumber;
    private String destinationBankCode;
    private BigDecimal amount;
    private String currency;
    private String category;
    private String remarks;
    private String status;
    private String failureCode;
    private String failureReason;
    private UUID reversedByTransactionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant completedAt;
}
