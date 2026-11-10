package com.group2.fse.account_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "MEMBER_SHARE_CAPITAL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberShareCapital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_capital_id")
    private Long shareCapitalId;

    @Column(name = "certificate_no", length = 64, nullable = false, unique = true)
    private String certificateNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "subscribed_shares", nullable = false)
    private Integer subscribedShares;

    @Column(name = "paid_up_shares", nullable = false)
    private Integer paidUpShares;

    @Builder.Default
    @Column(name = "par_value", precision = 18, scale = 4, nullable = false)
    private BigDecimal parValue = BigDecimal.valueOf(100).setScale(4);

    @Builder.Default
    @Column(name = "total_paid_amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal totalPaidAmount = BigDecimal.ZERO.setScale(4);

    @Builder.Default
    @Column(name = "minimum_required_shares", nullable = false)
    private Integer minimumRequiredShares = 20;

    @Builder.Default
    @Column(name = "dividend_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal dividendRate = BigDecimal.valueOf(7.25);

    @Builder.Default
    @Column(name = "membership_type", length = 20, nullable = false)
    private String membershipType = "REGULAR";

    @Builder.Default
    @Column(name = "has_voting_rights", nullable = false)
    private Boolean hasVotingRights = true;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (parValue == null) {
            parValue = BigDecimal.valueOf(100).setScale(4);
        }
        if (totalPaidAmount == null) {
            totalPaidAmount = BigDecimal.ZERO.setScale(4);
        }
        if (minimumRequiredShares == null) {
            minimumRequiredShares = 20;
        }
        if (dividendRate == null) {
            dividendRate = BigDecimal.valueOf(7.25);
        }
        if (membershipType == null) {
            membershipType = "REGULAR";
        }
        if (hasVotingRights == null) {
            hasVotingRights = (paidUpShares != null && paidUpShares >= minimumRequiredShares);
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.paidUpShares != null && this.minimumRequiredShares != null) {
            this.hasVotingRights = (this.paidUpShares >= this.minimumRequiredShares);
            this.membershipType = this.hasVotingRights ? "REGULAR" : "ASSOCIATE";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemberShareCapital that = (MemberShareCapital) o;
        return shareCapitalId != null && Objects.equals(shareCapitalId, that.shareCapitalId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
