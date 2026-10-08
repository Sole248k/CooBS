package com.group2.fse.staff_bff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffCustomer360Response {

    private Long customerId;
    private String displayName;
    private String username;
    private String email;
    private String phone;
    private String membershipNumber;
    private String kycStatus;
    private String kycTier;
    private Integer creditScore;
    private String riskScore;
    private List<StaffAccountSummary> accounts;
    private StaffTotals totals;
    private List<Map<String, Object>> recentTransactions;
    private Map<String, Object> spendingBreakdown;
    private List<Map<String, Object>> savedBeneficiaries;
    private String asOf;
    private List<String> partialFailures;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffTotals {
        private String totalDeposits;
        private String totalLoans;
    }
}
