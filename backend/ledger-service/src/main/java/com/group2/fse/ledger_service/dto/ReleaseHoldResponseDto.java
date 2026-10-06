package com.group2.fse.ledger_service.dto;

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
    private BigDecimal availableBalance;
}
