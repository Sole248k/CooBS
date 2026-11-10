package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Summary DTO for T8 internal recent transactions lookup (for Customer 360).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionSummaryDto {
    private UUID transactionId;
    private String referenceNo;
    private String direction;
    private String counterpartyName;
    private String counterpartyAccountNumber;
    private String amount;
    private String currency;
    private String category;
    private String status;
    private String failureCode;
    private String createdAt;
}
