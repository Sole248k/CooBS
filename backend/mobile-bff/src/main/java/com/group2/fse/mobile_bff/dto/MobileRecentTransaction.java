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
public class MobileRecentTransaction {

    private String transactionId;
    private String referenceNo;
    private String trackingReference;
    private String direction;
    private String amount;
    private String formattedAmount;
    private String currency;
    private String status;
    private String remarks;
    private String submittedAt;
    private String createdAt;
}
