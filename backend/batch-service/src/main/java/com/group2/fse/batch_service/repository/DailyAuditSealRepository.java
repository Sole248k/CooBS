package com.group2.fse.batch_service.repository;

import com.group2.fse.batch_service.entity.DailyAuditSeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyAuditSealRepository extends JpaRepository<DailyAuditSeal, Long> {
    Optional<DailyAuditSeal> findByBusinessDate(LocalDate businessDate);
}
