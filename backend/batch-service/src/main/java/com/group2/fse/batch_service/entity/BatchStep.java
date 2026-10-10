package com.group2.fse.batch_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "batch_step", schema = "batch", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"batch_run_id", "step_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_step_id")
    private Long batchStepId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_run_id", nullable = false)
    private BatchRun batchRun;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "step_name", nullable = false, length = 40)
    private String stepName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "items_processed", nullable = false)
    @Builder.Default
    private Integer itemsProcessed = 0;

    @Column(name = "duration_ms")
    private Integer durationMs;

    // What the step itself reported. Null until the step has run.
    @Column(name = "total_amount", precision = 18, scale = 4)
    private java.math.BigDecimal totalAmount;

    @Column(name = "message", length = 255)
    private String message;

    /** The total as the contract carries money: a string with two decimals. */
    public String totalAmountText() {
        return totalAmount == null ? null : totalAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
