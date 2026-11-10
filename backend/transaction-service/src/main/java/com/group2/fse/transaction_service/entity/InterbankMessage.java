package com.group2.fse.transaction_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One message exchanged with a partner bank (txn.interbank_message). For an outbound transfer
 * the message ID is the transaction ID, so that asking about the same transfer twice is asking
 * about the same message.
 */
@Entity
@Table(name = "interbank_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterbankMessage {

    public static final String OUTBOUND = "OUTBOUND";

    /** Accepted at T1; nothing has gone to the partner yet. */
    public static final String CREATED = "CREATED";
    /** Handed to the partner, or about to be: the partner may have it. No answer is recorded. */
    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "partner_bank_code", nullable = false, length = 8)
    private String partnerBankCode;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "counterparty_account", nullable = false, length = 34)
    private String counterpartyAccount;

    @Column(name = "counterparty_name", length = 120)
    private String counterpartyName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reason_code", length = 40)
    private String reasonCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean decided() {
        return ACCEPTED.equals(status) || REJECTED.equals(status);
    }
}
