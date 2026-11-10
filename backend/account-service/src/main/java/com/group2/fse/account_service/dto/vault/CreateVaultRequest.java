package com.group2.fse.account_service.dto.vault;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVaultRequest {

    private Long accountId;

    private String accountNumber;

    @NotBlank(message = "Vault name is mandatory")
    @Size(max = 128, message = "Vault name cannot exceed 128 characters")
    private String name;

    @NotNull(message = "Target amount is mandatory")
    @DecimalMin(value = "1.00", message = "Target amount must be at least 1.00")
    private BigDecimal targetAmount;

    private BigDecimal initialAmount;

    @NotBlank(message = "Target date is mandatory")
    @Size(max = 32, message = "Target date cannot exceed 32 characters")
    private String targetDate;

    @NotBlank(message = "Category is mandatory")
    @Size(max = 64, message = "Category cannot exceed 64 characters")
    private String category; // Emergency, Community, Cooperative, Education

    private Integer iconCode;

    private Long colorValue;
}
