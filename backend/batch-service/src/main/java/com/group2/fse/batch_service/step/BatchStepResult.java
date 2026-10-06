package com.group2.fse.batch_service.step;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result data holder produced by each batch step execution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchStepResult {
    private boolean success;
    private int itemsProcessed;
    private BigDecimal totalAmount;
    private String message;
}
