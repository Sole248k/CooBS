package com.group2.fse.transaction_service.repository;

import com.group2.fse.transaction_service.entity.TxnStageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TxnStageEventRepository extends JpaRepository<TxnStageEvent, Long> {

    List<TxnStageEvent> findByTransactionIdOrderByStageNoAsc(UUID transactionId);

    Optional<TxnStageEvent> findByTransactionIdAndStageNo(UUID transactionId, Integer stageNo);
}
