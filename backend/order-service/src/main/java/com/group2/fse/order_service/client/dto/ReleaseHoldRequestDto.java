package com.group2.fse.order_service.client.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseHoldRequestDto {

    private String reason;
}
