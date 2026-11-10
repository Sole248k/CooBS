package com.group2.fse.account_service.repository;

import com.group2.fse.account_service.entity.MemberShareCapital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberShareCapitalRepository extends JpaRepository<MemberShareCapital, Long> {

    Optional<MemberShareCapital> findByCustomerId(Long customerId);

    Optional<MemberShareCapital> findByCertificateNo(String certificateNo);
}
