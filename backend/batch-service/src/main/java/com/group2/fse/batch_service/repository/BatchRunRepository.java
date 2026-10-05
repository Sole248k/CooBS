package com.group2.fse.batch_service.repository;

import com.group2.fse.batch_service.entity.BatchRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {
    Optional<BatchRun> findByBusinessDate(LocalDate businessDate);
    boolean existsByStatus(String status);
    List<BatchRun> findAllByOrderByBatchRunIdDesc();
}
