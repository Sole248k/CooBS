package com.group2.fse.account_service.repository;

import com.group2.fse.account_service.entity.AccountVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountVaultRepository extends JpaRepository<AccountVault, Long> {
    List<AccountVault> findByCustomerIdAndStatusNotOrderByCreatedAtDesc(Long customerId, String status);
    List<AccountVault> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<AccountVault> findByVaultReference(String vaultReference);
    Optional<AccountVault> findByVaultIdAndCustomerId(Long vaultId, Long customerId);
    Optional<AccountVault> findByVaultReferenceAndCustomerId(String vaultReference, Long customerId);
}
