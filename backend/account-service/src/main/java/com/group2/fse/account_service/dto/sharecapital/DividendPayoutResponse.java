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
public class DividendPayoutResponse {

    private Long dividendId;
    private Integer fiscalYear;
    private BigDecimal shareCapitalDividend;
    private BigDecimal patronageRefund;
    private BigDecimal totalPayout;
    private String disposition;
    private LocalDateTime payoutDate;
    private String referenceNo;
    private String status;
}
