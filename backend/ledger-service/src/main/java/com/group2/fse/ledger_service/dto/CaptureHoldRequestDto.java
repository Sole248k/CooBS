package com.group2.fse.ledger_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptureHoldRequestDto {

    @NotNull(message = "Destination account ID is required")
    private Long destinationAccountId;

    private String remarks;
    private Long actorId;
    private String clientIp;
}
