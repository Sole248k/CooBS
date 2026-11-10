package com.group2.fse.ledger_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Task: Day 4 Core Transfer Flow
 * Transactional Outbox Entity mapping to OUTBOX table in Oracle System of Record.
 * Appended inside the exact same atomic transaction boundary as balance updates/hold capture.
 */
@Entity
@Table(name = "OUTBOX")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "topic", length = 100, nullable = false)
    private String topic;

    @Column(name = "event_key", length = 100, nullable = false)
    private String eventKey;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
