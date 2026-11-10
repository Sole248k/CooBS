package com.group2.fse.staff_bff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffAccountSummary {

    private Long accountId;
    private String accountNumber;
    private String accountType;
    private String currency;
    private String status;
    private String availableBalance;
    private String ledgerBalance;
    private String heldAmount;
    private String outstandingPrincipal;
    private String nextDueDate;
    private String nextDueAmount;
    private List<String> activeFlags;
}
