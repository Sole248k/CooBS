package com.group2.fse.account_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "SHARE_CAPITAL_TRANSACTION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareCapitalTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tx_id")
    private Long txId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_capital_id", nullable = false)
    private MemberShareCapital memberShareCapital;

    @Column(name = "transaction_type", length = 30, nullable = false)
    private String transactionType;

    @Column(name = "amount", precision = 18, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "shares_added", nullable = false)
    private Integer sharesAdded;

    @Column(name = "balance_after", precision = 18, scale = 4, nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "source_account_id")
    private Long sourceAccountId;

    @Column(name = "reference_no", length = 64, nullable = false)
    private String referenceNo;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
