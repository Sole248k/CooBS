package com.group2.fse.order_service.client.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceHoldResponseDto {

    private Long holdId;
    private String referenceNo;
    private Long accountId;
    private BigDecimal amount;
    private String status;
    private BigDecimal availableBalance;
    private BigDecimal ledgerBalance;
    private String createdAt;
}
