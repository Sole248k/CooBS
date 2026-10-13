package com.group2.fse.order_service.client.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptureHoldRequestDto {

    private Long destinationAccountId;
    private String remarks;
    private Long actorId;
    private String clientIp;
}
