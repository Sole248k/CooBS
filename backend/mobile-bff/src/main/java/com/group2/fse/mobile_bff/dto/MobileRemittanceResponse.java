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
public class MobileRemittanceResponse {

    private String transactionId;
    private String referenceNo;
    private String status;
    private String amount;
    private String currency;
    private String createdAt;
    private String timelineUrl;
}
