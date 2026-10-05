package com.group2.fse.ledger_service.repository;

import com.group2.fse.ledger_service.entity.Hold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HoldRepository extends JpaRepository<Hold, Long> {

    Optional<Hold> findByReferenceNo(String referenceNo);
}
