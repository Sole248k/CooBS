package com.group2.fse.customer_service.repository;

import com.group2.fse.customer_service.entity.Customer;
import com.group2.fse.customer_service.entity.Kyc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycRepository extends JpaRepository<Kyc, Long> {
    Optional<Kyc> findByCustomer(Customer customer);
    Optional<Kyc> findByCustomer_CustomerId(Long customerId);
}
