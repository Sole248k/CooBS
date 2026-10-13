package com.group2.fse.account_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PATRONAGE_DIVIDEND_PAYOUT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatronageDividendPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dividend_id")
    private Long dividendId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_capital_id", nullable = false)
    private MemberShareCapital memberShareCapital;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "share_capital_dividend", precision = 18, scale = 4, nullable = false)
    private BigDecimal shareCapitalDividend;

    @Column(name = "patronage_refund", precision = 18, scale = 4, nullable = false)
    private BigDecimal patronageRefund;

    @Column(name = "total_payout", precision = 18, scale = 4, nullable = false)
    private BigDecimal totalPayout;

    @Builder.Default
    @Column(name = "disposition", length = 30, nullable = false)
    private String disposition = "CREDITED_TO_SAVINGS";

    @Column(name = "payout_date", nullable = false)
    private LocalDateTime payoutDate;

    @Column(name = "reference_no", length = 64, nullable = false)
    private String referenceNo;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PAID";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (disposition == null) {
            disposition = "CREDITED_TO_SAVINGS";
        }
        if (status == null) {
            status = "PAID";
        }
    }
}
