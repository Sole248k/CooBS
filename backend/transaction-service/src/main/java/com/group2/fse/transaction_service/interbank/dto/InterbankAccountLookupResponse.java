package com.group2.fse.transaction_service.interbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Interbank account lookup response matching Section 7 of INTERBANK_TRANSFER_PROPOSAL.md.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterbankAccountLookupResponse {

    private String accountNumber;
    private boolean exists;
    private Boolean active;
    private String maskedName;
    private String currency;

    public static InterbankAccountLookupResponse found(String accountNumber, boolean active, String maskedName, String currency) {
        return InterbankAccountLookupResponse.builder()
                .accountNumber(accountNumber)
                .exists(true)
                .active(active)
                .maskedName(maskedName)
                .currency(currency)
                .build();
    }

    public static InterbankAccountLookupResponse notFound(String accountNumber) {
        return InterbankAccountLookupResponse.builder()
                .accountNumber(accountNumber)
                .exists(false)
                .build();
    }
}
