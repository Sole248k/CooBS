package com.group2.fse.mobile_bff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Server-authoritative remittance form options for the mobile app.
 * Limits mirror the validation applied to {@link MobileRemittanceRequest}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileRemittanceOptionsResponse {

    private String currency;
    private Integer scale;
    private Limits limits;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limits {
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
    }
}
