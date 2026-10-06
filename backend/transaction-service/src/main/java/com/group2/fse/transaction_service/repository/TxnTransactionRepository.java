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

    List<TxnTransaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(Long sourceAccountId, Long destinationAccountId);
}
