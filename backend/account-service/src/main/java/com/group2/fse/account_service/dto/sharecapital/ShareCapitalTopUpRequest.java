package com.group2.fse.account_service.dto.sharecapital;

import jakarta.validation.constraints.DecimalMin;
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
public class ShareCapitalTopUpRequest {

    @NotNull(message = "Top-up amount is required")
    @DecimalMin(value = "10.00", message = "Minimum top-up amount is ₱10.00")
    private BigDecimal amount;

    private Long sourceAccountId;
    private String sourceAccountNumber;
    private String notes;
}
