package com.group2.fse.account_service.repository;

import com.group2.fse.account_service.entity.PatronageDividendPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatronageDividendPayoutRepository extends JpaRepository<PatronageDividendPayout, Long> {

    List<PatronageDividendPayout> findByCustomerIdOrderByFiscalYearDesc(Long customerId);
}
