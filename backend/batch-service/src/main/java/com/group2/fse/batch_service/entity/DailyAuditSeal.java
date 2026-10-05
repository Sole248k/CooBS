package com.group2.fse.batch_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_audit_seal", schema = "audit_store")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyAuditSeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seal_id")
    private Long sealId;

    @Column(name = "business_date", nullable = false, unique = true)
    private LocalDate businessDate;

    @Column(name = "last_audit_id", nullable = false)
    private Long lastAuditId;

    @Column(name = "seal_hash", nullable = false, length = 64)
    private String sealHash;

    @Column(name = "sealed_at", nullable = false)
    private Instant sealedAt;
}
