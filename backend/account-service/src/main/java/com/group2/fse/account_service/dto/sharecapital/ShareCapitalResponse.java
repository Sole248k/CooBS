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
public class ShareCapitalResponse {

    private Long shareCapitalId;
    private String certificateNo;
    private Long customerId;
    private Long accountId;
    private String accountNumber;
    private Integer subscribedShares;
    private Integer paidUpShares;
    private BigDecimal parValue;
    private BigDecimal totalPaidAmount;
    private BigDecimal subscribedAmount;
    private Integer minimumRequiredShares;
    private BigDecimal dividendRate;
    private String membershipType;
    private Boolean hasVotingRights;
    private Double completionRatio;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
