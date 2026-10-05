package com.group2.fse.transaction_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalRequestDto {

    @NotBlank(message = "Original transaction reference or ID is required.")
    private String originalReferenceNo;

    private BigDecimal amount;

    @NotBlank(message = "Reversal reason is required.")
    private String reason;
}
