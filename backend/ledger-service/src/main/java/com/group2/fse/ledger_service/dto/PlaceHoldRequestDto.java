package com.group2.fse.ledger_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceHoldRequestDto {

    private String transactionId;

    // 34, not 36: capture appends -D / -C and TRANSACTION.reference_no is VARCHAR2(36)
    @NotBlank(message = "Reference number is required")
    @Size(max = 34, message = "referenceNo must not exceed 34 characters")
    private String referenceNo;

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
    @Digits(integer = 14, fraction = 4, message = "amount must have at most 4 decimal places")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "PHP";

    private Long actorId;
}
