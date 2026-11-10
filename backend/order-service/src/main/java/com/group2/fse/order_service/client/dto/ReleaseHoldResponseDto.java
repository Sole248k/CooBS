package com.group2.fse.order_service.client.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseHoldResponseDto {

    private Long holdId;
    private String status;
    private Long accountId;
    private BigDecimal amount;
    private BigDecimal availableBalance;
    private BigDecimal ledgerBalance;
    private String releasedAt;
}
