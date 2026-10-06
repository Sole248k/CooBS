package com.group2.fse.transaction_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "txn_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TxnTransaction {

    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "reference_no", nullable = false, unique = true, length = 36)
    private String referenceNo;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "source_account_id")
    private Long sourceAccountId;

    @Column(name = "destination_account_id")
    private Long destinationAccountId;

    @Column(name = "destination_account_number", length = 34)
    private String destinationAccountNumber;

    @Column(name = "destination_bank_code", nullable = false, length = 8)
    private String destinationBankCode;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Column(name = "reverses_transaction_id")
    private UUID reversesTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (currency == null) {
            currency = "PHP";
        }
        if (category == null) {
            category = "TRANSFER";
        }
        if (destinationBankCode == null) {
            destinationBankCode = "COOBS";
        }
        if (channel == null) {
            channel = "APP";
        }
    }
}
