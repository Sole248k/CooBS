package com.group2.fse.transaction_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for O1: POST /internal/v1/sagas/transfers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartTransferSagaRequest {
    private UUID transactionId;
    private String referenceNo;
    private String channel;
    private Long customerId;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private String destinationBankCode;
    private BigDecimal amount;
    private String currency;
    private Long actorId;
    private String actorType;
    // A wrapper type on purpose: with a primitive, the JSON name would lose its "is".
    private Boolean isNewBeneficiary;
}
