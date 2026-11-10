package com.group2.fse.staff_bff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffQueuesSummaryResponse {

    private int totalPendingKyc;
    private int totalPendingClosures;
    private List<Map<String, Object>> pendingKycRequests;
    private List<Map<String, Object>> pendingClosureRequests;
    private List<String> partialFailures;
}
