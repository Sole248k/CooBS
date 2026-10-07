package com.group2.fse.batch_service.repository;

import com.group2.fse.batch_service.entity.LoanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LoanScheduleRepository extends JpaRepository<LoanSchedule, Long> {
    List<LoanSchedule> findByDueDateLessThanEqualAndStatus(LocalDate date, String status);
    List<LoanSchedule> findByDueDateLessThanEqualAndStatusIn(LocalDate date, Collection<String> statuses);
}
