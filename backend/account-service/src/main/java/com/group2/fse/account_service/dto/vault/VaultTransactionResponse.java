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
public class VaultTransactionResponse {

    private Long vaultTxId;
    private Long vaultId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String referenceNo;
    private LocalDateTime createdAt;
}
