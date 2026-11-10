package com.group2.fse.ledger_service.reversal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reversal response payload adhering to Handshake H3 L7 contract.
 * Contains both strict L7 contract fields and dual-compatibility aliases.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReversalResponseDto {

    private String reversalReferenceNo;
    private String originalReferenceNo;
    private String status; // "POSTED"
    private List<ReversalEntryDto> entries;
    private Instant timestamp;

    // Dual-compatibility alias fields
    private String reversalId;
    private String originalTransactionId;
    private BigDecimal reversedAmount;
    private String currency;
}
