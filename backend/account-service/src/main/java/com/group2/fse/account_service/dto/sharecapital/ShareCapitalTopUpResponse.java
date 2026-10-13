package com.group2.fse.account_service.dto.sharecapital;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareCapitalTopUpResponse {

    private String referenceNo;
    private BigDecimal amountPaid;
    private Integer sharesAdded;
    private Integer newPaidUpShares;
    private BigDecimal newTotalPaidAmount;
    private Boolean hasVotingRights;
    private String membershipType;
    private BigDecimal sourceAccountAvailableBalance;
    private LocalDateTime timestamp;
}
