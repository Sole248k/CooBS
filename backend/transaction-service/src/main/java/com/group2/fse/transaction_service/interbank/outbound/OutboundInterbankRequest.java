package com.group2.fse.transaction_service.interbank.outbound;

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
 * I4 request, from the saga. Aligned with contracts/examples/I4-interbank-outbound.request.json.
 * The beneficiary's account and name are not here: they are read from the transaction record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundInterbankRequest {

    @NotNull(message = "transactionId is required")
    private UUID transactionId;

    private String referenceNo;

    @NotBlank(message = "destinationBankCode is required")
    private String destinationBankCode;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    private BigDecimal amount;

    private String currency;
}
