package com.group2.fse.risk_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitCheckResult {

    @Builder.Default
    private String outcome = "PASSED";

    private String usedToday;

    private String dailyLimit;
}
