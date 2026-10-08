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
public class StaffAdminOverviewResponse {

    private Map<String, Object> latestBatchRun;
    private int deadLetterQueueCount;
    private List<Map<String, Object>> deadLetterRecords;
    private Map<String, Object> latestAuditSeal;
    private List<String> partialFailures;
}
