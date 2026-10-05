package com.group2.fse.transaction_service.dto;

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
public class HoldRequestDto {

    @NotNull(message = "Account ID is required.")
    private Long accountId;

    @NotNull(message = "Hold amount is required.")
    @DecimalMin(value = "0.0001", message = "Hold amount must be greater than zero.")
    private BigDecimal amount;

    @NotBlank(message = "Hold reason is required.")
    private String reason;

    private String referenceNo;
}
