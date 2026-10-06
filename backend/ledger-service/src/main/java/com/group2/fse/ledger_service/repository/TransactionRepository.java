package com.group2.fse.ledger_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.group2.fse.ledger_service.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceNo(String referenceNo);

    boolean existsByReferenceNo(String referenceNo);

    List<Transaction> findByReferenceNoStartingWith(String prefix);

    boolean existsByReversesTransaction_TransactionId(Long transactionId);

    @Query("SELECT t FROM Transaction t WHERE t.referenceNo = :ref OR t.referenceNo LIKE CONCAT(:ref, '-%') ORDER BY t.transactionId ASC")
    List<Transaction> findByOriginalReferenceNo(@Param("ref") String ref);
}
