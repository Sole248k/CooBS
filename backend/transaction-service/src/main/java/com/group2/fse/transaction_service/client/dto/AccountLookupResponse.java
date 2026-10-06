package com.group2.fse.transaction_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO matching account-service AccountResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountLookupResponse {
    private Long accountId;
    private Long customerId;
    private String accountNumber;
    private String accountType;
    private String currency;
    private String status;
    private BigDecimal availableBalance;
}
