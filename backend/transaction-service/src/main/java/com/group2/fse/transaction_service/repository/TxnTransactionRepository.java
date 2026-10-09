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

    boolean existsByCustomerIdAndDestinationAccountIdAndStatusAndCreatedAtBefore(
            Long customerId, Long destinationAccountId, String status, java.time.Instant createdAt);

    boolean existsByCustomerIdAndDestinationBankCodeAndDestinationAccountNumberAndStatusAndCreatedAtBefore(
            Long customerId, String destinationBankCode, String destinationAccountNumber, String status,
            java.time.Instant createdAt);

    /**
     * True when the customer has no earlier transfer to that account that posted. This is what
     * the risk engine calls a new beneficiary. Only transfers created before this one count,
     * so the answer is the same when the saga start is sent again later.
     *
     * A transfer to another bank is matched on the bank and the account number there: its
     * destination account ID is the partner's settlement account, the same for every customer
     * of that bank.
     */
    default boolean isFirstPostedTransferTo(TxnTransaction txn) {
        if (txn.getDestinationBankCode() != null && !"COOBS".equalsIgnoreCase(txn.getDestinationBankCode())) {
            return !existsByCustomerIdAndDestinationBankCodeAndDestinationAccountNumberAndStatusAndCreatedAtBefore(
                    txn.getCustomerId(), txn.getDestinationBankCode(), txn.getDestinationAccountNumber(), "POSTED",
                    txn.getCreatedAt());
        }
        return !existsByCustomerIdAndDestinationAccountIdAndStatusAndCreatedAtBefore(
                txn.getCustomerId(), txn.getDestinationAccountId(), "POSTED", txn.getCreatedAt());
    }

    @Query("SELECT t FROM TxnTransaction t WHERE t.customerId = :customerId "
            + "OR t.sourceAccountId IN :accountIds "
            + "OR t.destinationAccountId IN :accountIds "
            + "OR t.destinationAccountNumber IN :accountNumbers "
            + "ORDER BY t.createdAt DESC")
    List<TxnTransaction> findByCustomerOrAccountDetails(
            @Param("customerId") Long customerId,
            @Param("accountIds") java.util.Collection<Long> accountIds,
            @Param("accountNumbers") java.util.Collection<String> accountNumbers);
}
