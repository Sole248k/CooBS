package com.group2.fse.transaction_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload sent to orchestration-service (O4: POST /internal/v1/sagas/inbound-interbank).
 * Matches InboundInterbankRequest record in orchestration-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundInterbankSagaRequest {

    @NotNull
    private UUID transactionId;

    @NotBlank
    private String referenceNo;

    private String messageId;

    private String sourceBankCode;

    @NotNull
    private Long settlementAccountId;

    @NotNull
    private Long destinationAccountId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String currency;
}
