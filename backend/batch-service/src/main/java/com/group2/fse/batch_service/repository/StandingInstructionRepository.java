package com.group2.fse.batch_service.repository;

import com.group2.fse.batch_service.entity.StandingInstruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StandingInstructionRepository extends JpaRepository<StandingInstruction, Long> {
    List<StandingInstruction> findByNextRunDateLessThanEqualAndActiveTrue(LocalDate date);
}
