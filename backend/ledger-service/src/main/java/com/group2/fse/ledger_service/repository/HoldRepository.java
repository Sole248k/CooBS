package com.group2.fse.ledger_service.repository;

import com.group2.fse.ledger_service.entity.Hold;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HoldRepository extends JpaRepository<Hold, Long> {

    Optional<Hold> findByReferenceNo(String referenceNo);

    /**
     * Locks the HOLD row so capture and release of the same hold are serialized.
     * Callers must take this lock before any BALANCE lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")
    })
    @Query("SELECT h FROM Hold h WHERE h.holdId = :holdId")
    Optional<Hold> findByIdForUpdate(@Param("holdId") Long holdId);
}
