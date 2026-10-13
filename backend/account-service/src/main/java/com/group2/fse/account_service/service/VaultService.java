package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.vault.CreateVaultRequest;
import com.group2.fse.account_service.dto.vault.VaultOperationRequest;
import com.group2.fse.account_service.dto.vault.VaultResponse;
import com.group2.fse.account_service.dto.vault.VaultTransactionResponse;

import java.util.List;

public interface VaultService {
    List<VaultResponse> getCustomerVaults(Long customerId);
    VaultResponse getVaultById(Long customerId, Long vaultId);
    VaultResponse getVaultByReference(Long customerId, String vaultReference);
    VaultResponse getVault(Long customerId, String vaultIdentifier);
    VaultResponse createVault(Long customerId, CreateVaultRequest request);
    VaultResponse allocateFunds(Long customerId, Long vaultId, VaultOperationRequest request);
    VaultResponse allocateFunds(Long customerId, String vaultIdentifier, VaultOperationRequest request);
    VaultResponse releaseFunds(Long customerId, Long vaultId, VaultOperationRequest request);
    VaultResponse releaseFunds(Long customerId, String vaultIdentifier, VaultOperationRequest request);
    VaultResponse closeVault(Long customerId, Long vaultId);
    VaultResponse closeVault(Long customerId, String vaultIdentifier);
    List<VaultTransactionResponse> getVaultTransactions(Long customerId, Long vaultId);
    List<VaultTransactionResponse> getVaultTransactions(Long customerId, String vaultIdentifier);
}

