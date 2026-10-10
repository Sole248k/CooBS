package com.group2.fse.transaction_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request payload for POST /api/v1/transactions/transfers (T1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequestDto {

    @NotNull(message = "Source account ID is required")
    private Long sourceAccountId;

    @NotBlank(message = "Destination account number is required")
    private String destinationAccountNumber;

    @Builder.Default
    private String destinationBankCode = "COOBS";

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    // The ledger's own rule. Without it an amount with five decimals passes risk and is refused at the hold.
    @Digits(integer = 14, fraction = 4, message = "Amount may have at most four decimal places")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "PHP";

    @Builder.Default
    private String category = "TRANSFER";

    private String remarks;

    private String beneficiaryName;
}
