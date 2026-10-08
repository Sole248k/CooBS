package com.group2.fse.mobile_bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileDashboardResponse {

    private MobileMemberSummary member;
    private MobileAccountSummary primaryAccount;
    @Builder.Default
    private int unreadNotifications = 0;
    @Builder.Default
    private List<MobileRecentTransaction> recentTransactions = new ArrayList<>();
    @Builder.Default
    private List<String> unavailable = new ArrayList<>();
    private String asOf;
}
