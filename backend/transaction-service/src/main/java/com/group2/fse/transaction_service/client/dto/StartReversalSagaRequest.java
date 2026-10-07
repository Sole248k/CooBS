package com.group2.fse.transaction_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * O2 request (contract section 8). {@code transactionId} is the new reversal transaction;
 * {@code reversesTransactionId} is the original.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartReversalSagaRequest {

    private UUID transactionId;
    private UUID reversesTransactionId;
    private String referenceNo;
    private String reason;
    private Long actorId;
}
