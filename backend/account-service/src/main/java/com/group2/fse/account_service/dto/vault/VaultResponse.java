package com.group2.fse.account_service.dto.vault;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VaultResponse {

    private String id; // Matches mobile IponVault.id (e.g. "VAULT-01" or reference)
    private Long vaultId;
    private String vaultReference;
    private Long customerId;
    private Long accountId;
    private String accountNumber;
    private String accountType;
    private String name;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private String targetDate;
    private String category;
    private Integer iconCode;
    private Long colorValue;
    private Double progress;
    private Boolean isCompleted;
    private BigDecimal remaining;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}
