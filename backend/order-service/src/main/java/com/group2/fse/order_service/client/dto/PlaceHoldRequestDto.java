package com.group2.fse.order_service.client.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceHoldRequestDto {

    private String transactionId;
    private String referenceNo;
    private Long accountId;
    private BigDecimal amount;
    @Builder.Default
    private String currency = "PHP";
    private Long actorId;
}
