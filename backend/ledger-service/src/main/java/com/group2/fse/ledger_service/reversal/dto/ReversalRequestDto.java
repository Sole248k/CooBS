package com.group2.fse.ledger_service.reversal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Reversal request payload matching Handshake H3 L7 contract.
 * Includes @JsonAlias for dual compatibility across orchestrators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalRequestDto {

    @NotBlank(message = "Original reference number or transaction ID is required")
    @JsonAlias({"originalTransactionId", "referenceNo", "originalRef"})
    private String originalReferenceNo;

    @JsonAlias({"reversalId", "reversalRef"})
    private String reversalReferenceNo;

    @JsonAlias({"reversalReason", "notes", "remarks"})
    private String reason;

    @JsonAlias({"requestedBy", "userId"})
    private Long actorId;
}
