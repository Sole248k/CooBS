package com.group2.fse.ledger_service.repository;

import com.group2.fse.ledger_service.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.outboxId ASC")
    List<OutboxEvent> findUnpublishedEvents(Pageable pageable);
}
