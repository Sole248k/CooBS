package com.group2.fse.orchestration_service.saga;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per saga. Matches saga.saga_instance in database/postgres-cloud/04_transaction_db.sql;
 * the schema comes from spring.jpa.properties.hibernate.default_schema.
 */
@Entity
@Table(name = "saga_instance")
@Getter
@Setter
@NoArgsConstructor
public class SagaInstance {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private SagaState state;

    @Column(name = "hold_id")
    private Long holdId;

    @Column(name = "risk_decision", length = 20)
    private String riskDecision;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SagaInstance(UUID transactionId) {
        this.sagaId = UUID.randomUUID();
        this.transactionId = transactionId;
        this.state = SagaState.STARTED;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
