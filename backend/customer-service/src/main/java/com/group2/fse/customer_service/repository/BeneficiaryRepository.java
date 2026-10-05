package com.group2.fse.customer_service.repository;

import com.group2.fse.customer_service.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByCustomerIdAndActiveTrue(Long customerId);
    Optional<Beneficiary> findByBeneficiaryIdAndCustomerId(Long beneficiaryId, Long customerId);
    boolean existsByCustomerIdAndAccountNumberAndActiveTrue(Long customerId, String accountNumber);
}
