package com.group2.fse.risk_engine.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResult {

    @Builder.Default
    private String outcome = "PASSED";

    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}
