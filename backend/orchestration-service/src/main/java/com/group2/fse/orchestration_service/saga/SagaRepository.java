package com.group2.fse.orchestration_service.saga;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaRepository extends JpaRepository<SagaInstance, UUID> {

    Optional<SagaInstance> findByTransactionId(UUID transactionId);

    Optional<SagaInstance> findByReferenceNo(String referenceNo);

    List<SagaInstance> findByStateIn(Collection<SagaState> states);

    List<SagaInstance> findByStateInAndUpdatedAtBefore(Collection<SagaState> states, Instant cutoff);

    /**
     * Saves a copy that was read earlier and brings its version up to date, so that the same
     * copy can be saved again by the step after this one.
     *
     * @throws org.springframework.dao.OptimisticLockingFailureException when the row was
     *         written by someone else after this copy was read. The caller reloads the row
     *         and decides again from what is saved; it never retries the same write.
     */
    default void store(SagaInstance saga) {
        SagaInstance saved = saveAndFlush(saga);
        saga.setVersion(saved.getVersion());
        saga.setUpdatedAt(saved.getUpdatedAt());
    }

    /** A repeated start for the same transaction returns the existing saga instead of a second one. */
    default SagaInstance findOrCreate(UUID transactionId) {
        return findOrCreate(transactionId, null, null);
    }

    /**
     * As above, for a saga that is run in the background. The payload goes in with the insert
     * that creates the row, so there is never a row the sweep cannot run again.
     */
    default SagaInstance findOrCreate(UUID transactionId, String referenceNo, String payload) {
        return findByTransactionId(transactionId).orElseGet(() -> {
            SagaInstance saga = new SagaInstance(transactionId);
            saga.setReferenceNo(referenceNo);
            saga.setPayload(payload);
            try {
                return saveAndFlush(saga);
            } catch (DataIntegrityViolationException raced) {
                return findByTransactionId(transactionId).orElseThrow();
            }
        });
    }
}
