package com.group2.fse.ledger_service.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptureHoldResponseDto {

    private Long holdId;
    private String status;
    private String transferReference;
    private Long debitTransactionId;
    private Long creditTransactionId;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private BigDecimal amount;
    private BigDecimal sourceNewBalance;
    private BigDecimal destinationNewBalance;
    private String auditHash;
    private String timestamp;
}
