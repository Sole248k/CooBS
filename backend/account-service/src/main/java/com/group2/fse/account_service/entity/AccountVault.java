package com.group2.fse.account_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ACCOUNT_VAULT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountVault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vault_id")
    private Long vaultId;

    @Column(name = "vault_reference", length = 64, nullable = false, unique = true)
    private String vaultReference;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "target_amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal targetAmount;

    @Builder.Default
    @Column(name = "current_amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal currentAmount = BigDecimal.ZERO.setScale(4);

    @Column(name = "target_date", length = 32, nullable = false)
    private String targetDate;

    @Column(name = "category", length = 64, nullable = false)
    private String category;

    @Builder.Default
    @Column(name = "icon_code", nullable = false)
    private Integer iconCode = 0xe59c;

    @Builder.Default
    @Column(name = "color_value", nullable = false)
    private Long colorValue = 0xFF0D9488L;

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
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (currentAmount == null) currentAmount = BigDecimal.ZERO.setScale(4);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountVault that = (AccountVault) o;
        return vaultId != null && Objects.equals(vaultId, that.vaultId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
