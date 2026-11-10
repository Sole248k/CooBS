package com.group2.fse.mobile_bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileAccountSummary {

    private Long accountId;
    private String accountNumber;
    private String accountType;
    private String currency;
    private String availableBalance;
    private String formattedBalance;
    private String status;
}
