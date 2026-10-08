package com.group2.fse.mobile_bff.dto;

import jakarta.validation.constraints.*;
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

    @Pattern(regexp = "^[A-Za-z0-9_-]{4,34}$", message = "Invalid source account number format")
    private String sourceAccountNumber;

    @Positive(message = "Source account ID must be positive")
    private Long sourceAccountId;

    @NotBlank(message = "Destination account number is required")
    @Size(min = 4, max = 34, message = "Destination account number must be between 4 and 34 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid destination account number format")
    private String destinationAccountNumber;

    @Builder.Default
    @Pattern(regexp = "^[A-Za-z0-9_]{3,10}$", message = "Invalid destination bank code format")
    private String destinationBankCode = "COOBS";

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @DecimalMax(value = "500000.00", message = "Amount exceeds regulatory ceiling limit of ₱500,000.00")
    @Digits(integer = 12, fraction = 2, message = "Amount cannot have more than 2 decimal places")
    private BigDecimal amount;

    @Builder.Default
    @Pattern(regexp = "^(PHP|USD)$", message = "Currency must be PHP or USD")
    private String currency = "PHP";

    @Builder.Default
    @Pattern(regexp = "^[A-Za-z0-9_]{2,20}$", message = "Invalid category")
    private String category = "TRANSFER";

    @Size(max = 150, message = "Remarks cannot exceed 150 characters")
    private String remarks;
}
