package com.group2.fse.account_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ACCOUNT_FLAG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flag_id")
    private Long flagId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "reason", length = 255, nullable = false)
    private String reason;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "flagged_by", nullable = false)
    private Long flaggedBy;

    @Column(name = "removed_by")
    private Long removedBy;

    @Builder.Default
    @Column(name = "flagged_at", nullable = false, updatable = false)
    private LocalDateTime flaggedAt = LocalDateTime.now();

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @PrePersist
    protected void onCreate() {
        if (flaggedAt == null) {
            flaggedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountFlag that = (AccountFlag) o;
        return flagId != null && Objects.equals(flagId, that.flagId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
