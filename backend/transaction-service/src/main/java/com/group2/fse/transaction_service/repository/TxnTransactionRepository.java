package com.group2.fse.transaction_service.repository;

import com.group2.fse.transaction_service.entity.TxnTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TxnTransactionRepository extends JpaRepository<TxnTransaction, UUID> {

    Optional<TxnTransaction> findByTransactionId(UUID transactionId);

    Optional<TxnTransaction> findByReferenceNo(String referenceNo);

    Optional<TxnTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM TxnTransaction t WHERE t.transactionId = :id OR t.referenceNo = :ref")
    Optional<TxnTransaction> findByIdOrReferenceNo(@Param("id") UUID id, @Param("ref") String ref);

    List<TxnTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    boolean existsByReversesTransactionIdAndStatusIn(UUID reversesTransactionId, java.util.Collection<String> statuses);

    Optional<TxnTransaction> findFirstByReversesTransactionIdAndStatus(UUID reversesTransactionId, String status);

    /**
     * Transfers accepted in the window that are still PROCESSING and show no stage from the saga
     * (4 or above). Reversals are left out: their saga start needs the staff member who asked,
     * and is sent again when that person repeats the request.
     */
    @Query("SELECT t FROM TxnTransaction t WHERE t.status = 'PROCESSING' AND t.reversesTransactionId IS NULL "
            + "AND t.createdAt > :notBefore AND t.createdAt < :before "
            + "AND NOT EXISTS (SELECT 1 FROM TxnStageEvent e WHERE e.transactionId = t.transactionId AND e.stageNo > 3)")
    List<TxnTransaction> findProcessingWithoutSagaStage(@Param("notBefore") java.time.Instant notBefore,
                                                        @Param("before") java.time.Instant before);

    List<TxnTransaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(Long sourceAccountId, Long destinationAccountId);
}
