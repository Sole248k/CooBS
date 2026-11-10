package com.group2.fse.account_service.repository;

import com.group2.fse.account_service.entity.ShareCapitalTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareCapitalTransactionRepository extends JpaRepository<ShareCapitalTransaction, Long> {

    List<ShareCapitalTransaction> findByMemberShareCapital_ShareCapitalIdOrderByCreatedAtDesc(Long shareCapitalId);
}
