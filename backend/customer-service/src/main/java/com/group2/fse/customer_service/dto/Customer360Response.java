package com.group2.fse.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Customer360Response {

    private Long customerId;
    private String displayName;
    private String membershipNumber;
    private String kycStatus;
    private String kycTier;
    private Integer creditScore;
    private String riskScore;

    @Builder.Default
    private List<AccountDto> accounts = new ArrayList<>();

    @Builder.Default
    private FinancialTotals totals = new FinancialTotals("0.00", "0.00");

    @Builder.Default
    private List<TransactionSummaryDto> recentTransactions = new ArrayList<>();

    @Builder.Default
    private SpendingBreakdown spendingBreakdown = new SpendingBreakdown(30, new ArrayList<>());

    @Builder.Default
    private List<BeneficiaryDto> beneficiaries = new ArrayList<>();

    private String asOf;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AccountDto {
        private Long accountId;
        private String accountNumber;
        private String accountType;
        private String currency;
        private String status;
        private String availableBalance;
        private String ledgerBalance;
        private String heldAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialTotals {
        private String deposits;
        private String loans;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionSummaryDto {
        private String transactionId;
        private String referenceNo;
        private String direction;
        private String counterpartyName;
        private String counterpartyAccountNumber;
        private String amount;
        private String currency;
        private String category;
        private String status;
        private String failureCode;
        private String createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SpendingBreakdown {
        @Builder.Default
        private Integer periodDays = 30;
        @Builder.Default
        private List<SpendingItemDto> items = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SpendingItemDto {
        private String category;
        private String amount;
        private Integer count;
    }
}
