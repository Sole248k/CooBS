package com.group2.fse.transaction_service.interbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound interbank transfer request payload matching Section 5 of INTERBANK_TRANSFER_PROPOSAL.md.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterbankTransferRequest {

    @NotBlank(message = "messageId is required")
    private String messageId;

    @NotBlank(message = "sourceBankCode is required")
    private String sourceBankCode;

    @NotBlank(message = "destinationBankCode is required")
    private String destinationBankCode;

    @NotNull(message = "sender is required")
    @Valid
    private Party sender;

    @NotNull(message = "beneficiary is required")
    @Valid
    private Beneficiary beneficiary;

    @NotBlank(message = "amount is required")
    @Pattern(regexp = "^\\d+(\\.\\d{2})?$", message = "amount must have at most two decimal places")
    private String amount;

    @NotBlank(message = "currency is required")
    private String currency;

    @Size(max = 140, message = "purpose must be at most 140 characters")
    private String purpose;

    @NotBlank(message = "senderReference is required")
    private String senderReference;

    @NotBlank(message = "createdAt is required")
    private String createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Party {
        @NotBlank(message = "sender.accountNumber is required")
        private String accountNumber;

        @NotBlank(message = "sender.name is required")
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Beneficiary {
        @NotBlank(message = "beneficiary.accountNumber is required")
        private String accountNumber;

        private String name;
    }
}
