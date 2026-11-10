package com.group2.fse.ledger_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "\"TRANSACTION\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "reference_no", length = 36, nullable = false, unique = true)
    private String referenceNo;

    @Column(name = "transaction_type", length = 30, nullable = false)
    private String transactionType;

    @Column(name = "amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "previous_balance", precision = 18, scale = 4, nullable = false)
    private BigDecimal previousBalance;

    @Column(name = "new_balance", precision = 18, scale = 4, nullable = false)
    private BigDecimal newBalance;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "COMPLETED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_transaction_id")
    private Transaction reversesTransaction;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "COMPLETED";
        }
        if (amount != null) {
            this.amount = this.amount.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (previousBalance != null) {
            this.previousBalance = this.previousBalance.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (newBalance != null) {
            this.newBalance = this.newBalance.setScale(4, java.math.RoundingMode.HALF_UP);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return transactionId != null && Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
