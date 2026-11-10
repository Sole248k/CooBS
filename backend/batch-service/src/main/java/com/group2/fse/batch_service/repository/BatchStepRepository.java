package com.group2.fse.batch_service.repository;

import com.group2.fse.batch_service.entity.BatchStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchStepRepository extends JpaRepository<BatchStep, Long> {
    List<BatchStep> findByBatchRun_BatchRunIdOrderByStepNoAsc(Long batchRunId);
}
