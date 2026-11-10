package com.group2.fse.order_service.client.dto;

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
    private Long sourceAccountId;
    private Long destinationAccountId;
    private BigDecimal amount;
    private Long debitTransactionId;
    private Long creditTransactionId;
    private String capturedAt;
}
