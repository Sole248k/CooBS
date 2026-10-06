package com.group2.fse.customer_service.repository;

import com.group2.fse.customer_service.entity.KycUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KycUpdateRequestRepository extends JpaRepository<KycUpdateRequest, Long> {
    Page<KycUpdateRequest> findByStatus(String status, Pageable pageable);
}
