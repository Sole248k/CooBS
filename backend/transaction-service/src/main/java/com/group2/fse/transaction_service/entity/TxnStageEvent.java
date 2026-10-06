package com.group2.fse.transaction_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "txn_stage_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_txn_stage_event", columnNames = {"transaction_id", "stage_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TxnStageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_event_id")
    private Long stageEventId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "stage_no", nullable = false)
    private Integer stageNo;

    @Column(name = "stage_name", nullable = false, length = 30)
    private String stageName;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    public void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
