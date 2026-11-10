package com.group2.fse.ledger_service.reversal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

/**
 * Individual balanced entry within an immutable ledger reversal.
 * Follows Handshake H3 L7 entry contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReversalEntryDto {

    private Long transactionId;
    private Long accountId;
    private String transactionType; // REVERSAL_CREDIT or REVERSAL_DEBIT
    private BigDecimal amount;
    private BigDecimal newBalance;
}
