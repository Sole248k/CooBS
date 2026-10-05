package com.group2.fse.orchestration_service.saga;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByTransactionId(UUID transactionId);

    /** A repeated start for the same transaction returns the existing saga instead of a second one. */
    default SagaInstance findOrCreate(UUID transactionId) {
        return findByTransactionId(transactionId).orElseGet(() -> {
            try {
                return saveAndFlush(new SagaInstance(transactionId));
            } catch (DataIntegrityViolationException raced) {
                return findByTransactionId(transactionId).orElseThrow();
            }
        });
    }
}
