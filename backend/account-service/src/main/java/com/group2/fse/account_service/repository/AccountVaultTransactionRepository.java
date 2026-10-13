package com.group2.fse.account_service.repository;

import com.group2.fse.account_service.entity.AccountVaultTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountVaultTransactionRepository extends JpaRepository<AccountVaultTransaction, Long> {
    List<AccountVaultTransaction> findByVault_VaultIdOrderByCreatedAtDesc(Long vaultId);
}
