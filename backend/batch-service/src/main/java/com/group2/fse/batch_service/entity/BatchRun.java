package com.group2.fse.batch_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "batch_run", schema = "batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_run_id")
    private Long batchRunId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "triggered_by", nullable = false)
    private Long triggeredBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "batchRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepNo ASC")
    @Builder.Default
    private List<BatchStep> steps = new ArrayList<>();
}
