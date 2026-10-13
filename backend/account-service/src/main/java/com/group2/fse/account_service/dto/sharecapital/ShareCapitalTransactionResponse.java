package com.group2.fse.account_service.dto.sharecapital;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareCapitalTransactionResponse {

    private Long txId;
    private String transactionType;
    private BigDecimal amount;
    private Integer sharesAdded;
    private BigDecimal balanceAfter;
    private String referenceNo;
    private LocalDateTime createdAt;
}
