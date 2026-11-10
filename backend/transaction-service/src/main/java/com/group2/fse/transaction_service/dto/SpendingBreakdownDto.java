package com.group2.fse.transaction_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Breakdown DTO for T9 internal spending category aggregation (for Customer 360).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpendingBreakdownDto {

    @Builder.Default
    private Integer periodDays = 30;

    @Builder.Default
    private List<SpendingItemDto> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SpendingItemDto {
        private String category;
        private String amount;
        private Integer count;
    }
}
