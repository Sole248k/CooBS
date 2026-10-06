package com.group2.fse.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskProfileResponse {
    private Long customerId;
    private String riskScore;
    private String kycTier;
    private String kycStatus;
    private String dailyLimit;
}
