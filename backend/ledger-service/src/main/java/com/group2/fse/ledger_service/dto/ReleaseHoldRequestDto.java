package com.group2.fse.ledger_service.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseHoldRequestDto {

    private String reason;
}
