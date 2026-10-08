package com.group2.fse.mobile_bff.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileRemittanceRequest {

    private String sourceAccountNumber;

    private Long sourceAccountId;

    @NotBlank(message = "Destination account number is required")
    private String destinationAccountNumber;

    @Builder.Default
    private String destinationBankCode = "COOBS";

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "PHP";

    @Builder.Default
    private String category = "TRANSFER";

    private String remarks;
}
