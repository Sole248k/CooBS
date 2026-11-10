package com.group2.fse.transaction_service.interbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound interbank response payload matching Section 5 and Section 6 of INTERBANK_TRANSFER_PROPOSAL.md.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterbankTransferResponse {

    private String messageId;
    private String status; // ACCEPTED, COMPLETED, REJECTED, or NOT_FOUND
    @com.fasterxml.jackson.annotation.JsonAlias({"referenceNumber", "receiverReference"})
    private String receiverReference;
    private String beneficiaryName;
    private String creditedAt;
    private String reasonCode;
    private String reasonMessage;
    private String rejectedAt;

    public static InterbankTransferResponse accepted(String messageId, String receiverReference, String beneficiaryName, String creditedAt) {
        return InterbankTransferResponse.builder()
                .messageId(messageId)
                .status("ACCEPTED")
                .receiverReference(receiverReference)
                .beneficiaryName(beneficiaryName)
                .creditedAt(creditedAt)
                .build();
    }

    public static InterbankTransferResponse rejected(String messageId, String reasonCode, String reasonMessage, String rejectedAt) {
        return InterbankTransferResponse.builder()
                .messageId(messageId)
                .status("REJECTED")
                .reasonCode(reasonCode)
                .reasonMessage(reasonMessage)
                .rejectedAt(rejectedAt)
                .build();
    }

    public static InterbankTransferResponse notFound(String messageId) {
        return InterbankTransferResponse.builder()
                .messageId(messageId)
                .status("NOT_FOUND")
                .build();
    }
}
