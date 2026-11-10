package com.group2.fse.account_service.service.impl;

import com.group2.fse.account_service.dto.vault.*;
import com.group2.fse.account_service.entity.Account;
import com.group2.fse.account_service.entity.AccountVault;
import com.group2.fse.account_service.entity.AccountVaultTransaction;
import com.group2.fse.account_service.entity.Balance;
import com.group2.fse.account_service.exception.AccountNotFoundException;
import com.group2.fse.account_service.exception.InsufficientAccountFundsException;
import com.group2.fse.account_service.exception.InsufficientVaultFundsException;
import com.group2.fse.account_service.exception.VaultNotFoundException;
import com.group2.fse.account_service.repository.AccountRepository;
import com.group2.fse.account_service.repository.AccountVaultRepository;
import com.group2.fse.account_service.repository.AccountVaultTransactionRepository;
import com.group2.fse.account_service.repository.BalanceRepository;
import com.group2.fse.account_service.service.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    private final AccountVaultRepository accountVaultRepository;
    private final AccountVaultTransactionRepository accountVaultTransactionRepository;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final Random random = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public List<VaultResponse> getCustomerVaults(Long customerId) {
        log.info("Fetching active Ipon vaults for customer ID {}", customerId);
        List<AccountVault> vaults = accountVaultRepository.findByCustomerIdAndStatusNotOrderByCreatedAtDesc(customerId, "CLOSED");
        return vaults.stream()
                .map(this::mapToVaultResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VaultResponse getVaultById(Long customerId, Long vaultId) {
        AccountVault vault = accountVaultRepository.findByVaultIdAndCustomerId(vaultId, customerId)
                .orElseThrow(() -> new VaultNotFoundException("Vault not found with ID: " + vaultId));
        return mapToVaultResponse(vault);
    }

    @Override
    @Transactional(readOnly = true)
    public VaultResponse getVaultByReference(Long customerId, String vaultReference) {
        AccountVault vault = accountVaultRepository.findByVaultReferenceAndCustomerId(vaultReference, customerId)
                .orElseThrow(() -> new VaultNotFoundException("Vault not found with reference: " + vaultReference));
        return mapToVaultResponse(vault);
    }

    @Override
    @Transactional
    public VaultResponse createVault(Long customerId, CreateVaultRequest request) {
        log.info("Creating new Ipon Vault '{}' for customer ID {}", request.getName(), customerId);

        Account account = resolveCustomerAccount(customerId, request.getAccountId(), request.getAccountNumber());
        String vaultRef = generateUniqueVaultReference();

        AccountVault vault = AccountVault.builder()
                .vaultReference(vaultRef)
                .customerId(customerId)
                .account(account)
                .name(request.getName().trim())
                .targetAmount(request.getTargetAmount().setScale(4, RoundingMode.HALF_UP))
                .currentAmount(BigDecimal.ZERO.setScale(4))
                .targetDate(request.getTargetDate().trim())
                .category(request.getCategory().trim())
                .iconCode(request.getIconCode() != null ? request.getIconCode() : 0xe59c)
                .colorValue(request.getColorValue() != null ? request.getColorValue() : 0xFF0D9488L)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        AccountVault savedVault = accountVaultRepository.save(vault);

        if (request.getInitialAmount() != null && request.getInitialAmount().compareTo(BigDecimal.ZERO) > 0) {
            allocateInternal(customerId, savedVault, request.getInitialAmount(), "Initial goal allocation");
        }

        log.info("Successfully created Ipon Vault ID {} (Ref: {}) for customer ID {}",
                savedVault.getVaultId(), savedVault.getVaultReference(), customerId);

        return mapToVaultResponse(savedVault);
    }

    @Override
    @Transactional
    public VaultResponse allocateFunds(Long customerId, Long vaultId, VaultOperationRequest request) {
        log.info("Allocating {} to vault ID {} by customer ID {}", request.getAmount(), vaultId, customerId);

        AccountVault vault = accountVaultRepository.findByVaultIdAndCustomerId(vaultId, customerId)
                .orElseThrow(() -> new VaultNotFoundException("Vault not found with ID: " + vaultId));

        if ("CLOSED".equalsIgnoreCase(vault.getStatus())) {
            throw new IllegalStateException("Cannot allocate funds to a closed vault");
        }

        allocateInternal(customerId, vault, request.getAmount(), request.getNotes() != null ? request.getNotes() : "Earmarked allocation");
        return mapToVaultResponse(vault);
    }

    @Override
    @Transactional
    public VaultResponse releaseFunds(Long customerId, Long vaultId, VaultOperationRequest request) {
        log.info("Releasing {} from vault ID {} by customer ID {}", request.getAmount(), vaultId, customerId);

        AccountVault vault = accountVaultRepository.findByVaultIdAndCustomerId(vaultId, customerId)
                .orElseThrow(() -> new VaultNotFoundException("Vault not found with ID: " + vaultId));

        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Release amount must be greater than zero");
        }

        if (vault.getCurrentAmount().compareTo(amount) < 0) {
            throw new InsufficientVaultFundsException(String.format(
                    "Vault only holds %.2f, cannot release %.2f",
                    vault.getCurrentAmount().doubleValue(), amount.doubleValue()));
        }

        Account account = vault.getAccount();
        Balance balance = balanceRepository.findByAccount_AccountId(account.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Balance record not found for account ID: " + account.getAccountId()));

        // 1. Decrement vault funds
        vault.setCurrentAmount(vault.getCurrentAmount().subtract(amount).setScale(4, RoundingMode.HALF_UP));
        if (vault.getCurrentAmount().compareTo(vault.getTargetAmount()) < 0 && "COMPLETED".equals(vault.getStatus())) {
            vault.setStatus("ACTIVE");
        }
        vault.setUpdatedAt(LocalDateTime.now());
        accountVaultRepository.save(vault);

        // 2. Restore available deposit balance
        balance.setAvailableBalance(balance.getAvailableBalance().add(amount).setScale(4, RoundingMode.HALF_UP));
        balance.setUpdatedAt(LocalDateTime.now());
        balanceRepository.save(balance);

        // 3. Record transaction
        String ref = "VTX-REL-" + (System.currentTimeMillis() % 1000000);
        AccountVaultTransaction tx = AccountVaultTransaction.builder()
                .vault(vault)
                .transactionType("RELEASE")
                .amount(amount)
                .balanceAfter(vault.getCurrentAmount())
                .referenceNo(ref)
                .createdAt(LocalDateTime.now())
                .build();
        accountVaultTransactionRepository.save(tx);

        log.info("Released {} from vault {} back to account {}. New vault balance: {}",
                amount, vault.getVaultReference(), account.getAccountNumber(), vault.getCurrentAmount());

        return mapToVaultResponse(vault);
    }

    @Override
    @Transactional
    public VaultResponse closeVault(Long customerId, Long vaultId) {
        log.info("Closing vault ID {} for customer ID {}", vaultId, customerId);

        AccountVault vault = accountVaultRepository.findByVaultIdAndCustomerId(vaultId, customerId)
                .orElseThrow(() -> new VaultNotFoundException("Vault not found with ID: " + vaultId));

        // Release any remaining balance back to member deposit account
        if (vault.getCurrentAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = vault.getCurrentAmount();
            Account account = vault.getAccount();
            Balance balance = balanceRepository.findByAccount_AccountId(account.getAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Balance not found for account: " + account.getAccountId()));

            balance.setAvailableBalance(balance.getAvailableBalance().add(remaining).setScale(4, RoundingMode.HALF_UP));
            balance.setUpdatedAt(LocalDateTime.now());
            balanceRepository.save(balance);

            AccountVaultTransaction tx = AccountVaultTransaction.builder()
                    .vault(vault)
                    .transactionType("RELEASE")
                    .amount(remaining)
                    .balanceAfter(BigDecimal.ZERO.setScale(4))
                    .referenceNo("VTX-CLOSE-" + (System.currentTimeMillis() % 1000000))
                    .createdAt(LocalDateTime.now())
                    .build();
            accountVaultTransactionRepository.save(tx);

            vault.setCurrentAmount(BigDecimal.ZERO.setScale(4));
        }

        vault.setStatus("CLOSED");
        vault.setUpdatedAt(LocalDateTime.now());
        accountVaultRepository.save(vault);

        log.info("Closed vault {} (ID {}) and released all funds back to account",
                vault.getVaultReference(), vaultId);

        return mapToVaultResponse(vault);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VaultTransactionResponse> getVaultTransactions(Long customerId, Long vaultId) {
        AccountVault vault = accountVaultRepository.findByVaultIdAndCustomerId(vaultId, customerId)
                .orElseThrow(() -> new VaultNotFoundException("Vault not found with ID: " + vaultId));

        return accountVaultTransactionRepository.findByVault_VaultIdOrderByCreatedAtDesc(vault.getVaultId())
                .stream()
                .map(tx -> VaultTransactionResponse.builder()
                        .vaultTxId(tx.getVaultTxId())
                        .vaultId(vault.getVaultId())
                        .transactionType(tx.getTransactionType())
                        .amount(tx.getAmount())
                        .balanceAfter(tx.getBalanceAfter())
                        .referenceNo(tx.getReferenceNo())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VaultResponse getVault(Long customerId, String vaultIdentifier) {
        AccountVault vault = resolveVault(customerId, vaultIdentifier);
        return mapToVaultResponse(vault);
    }

    @Override
    @Transactional
    public VaultResponse allocateFunds(Long customerId, String vaultIdentifier, VaultOperationRequest request) {
        AccountVault vault = resolveVault(customerId, vaultIdentifier);
        if ("CLOSED".equalsIgnoreCase(vault.getStatus())) {
            throw new IllegalStateException("Cannot allocate funds to a closed vault");
        }
        allocateInternal(customerId, vault, request.getAmount(), request.getNotes() != null ? request.getNotes() : "Earmarked allocation");
        return mapToVaultResponse(vault);
    }

    @Override
    @Transactional
    public VaultResponse releaseFunds(Long customerId, String vaultIdentifier, VaultOperationRequest request) {
        AccountVault vault = resolveVault(customerId, vaultIdentifier);
        return releaseFunds(customerId, vault.getVaultId(), request);
    }

    @Override
    @Transactional
    public VaultResponse closeVault(Long customerId, String vaultIdentifier) {
        AccountVault vault = resolveVault(customerId, vaultIdentifier);
        return closeVault(customerId, vault.getVaultId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VaultTransactionResponse> getVaultTransactions(Long customerId, String vaultIdentifier) {
        AccountVault vault = resolveVault(customerId, vaultIdentifier);
        return getVaultTransactions(customerId, vault.getVaultId());
    }

    private AccountVault resolveVault(Long customerId, String identifier) {
        try {
            Long numericId = Long.parseLong(identifier);
            var byId = accountVaultRepository.findByVaultIdAndCustomerId(numericId, customerId);
            if (byId.isPresent()) return byId.get();
        } catch (NumberFormatException ignored) {
        }
        return accountVaultRepository.findByVaultReferenceAndCustomerId(identifier, customerId)
                .orElseThrow(() -> new VaultNotFoundException("Vault not found with identifier: " + identifier));
    }

    private void allocateInternal(Long customerId, AccountVault vault, BigDecimal rawAmount, String notes) {

        BigDecimal amount = rawAmount.setScale(4, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Allocation amount must be greater than zero");
        }

        Account account = vault.getAccount();
        Balance balance = balanceRepository.findByAccount_AccountId(account.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Balance record not found for account: " + account.getAccountId()));

        if (balance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientAccountFundsException(String.format(
                    "Insufficient available funds in %s. Available: %.2f, Required: %.2f",
                    account.getAccountNumber(), balance.getAvailableBalance().doubleValue(), amount.doubleValue()));
        }

        // 1. Decrement available balance
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(amount).setScale(4, RoundingMode.HALF_UP));
        balance.setUpdatedAt(LocalDateTime.now());
        balanceRepository.save(balance);

        // 2. Increment vault currentAmount
        vault.setCurrentAmount(vault.getCurrentAmount().add(amount).setScale(4, RoundingMode.HALF_UP));
        if (vault.getCurrentAmount().compareTo(vault.getTargetAmount()) >= 0) {
            vault.setStatus("COMPLETED");
        }
        vault.setUpdatedAt(LocalDateTime.now());
        accountVaultRepository.save(vault);

        // 3. Record transaction
        String ref = "VTX-ALC-" + (System.currentTimeMillis() % 1000000);
        AccountVaultTransaction tx = AccountVaultTransaction.builder()
                .vault(vault)
                .transactionType("ALLOCATE")
                .amount(amount)
                .balanceAfter(vault.getCurrentAmount())
                .referenceNo(ref)
                .createdAt(LocalDateTime.now())
                .build();
        accountVaultTransactionRepository.save(tx);

        log.info("Allocated {} to vault {} from account {}. New vault balance: {}, Account available balance: {}",
                amount, vault.getVaultReference(), account.getAccountNumber(), vault.getCurrentAmount(), balance.getAvailableBalance());
    }

    private Account resolveCustomerAccount(Long customerId, Long accountId, String accountNumber) {
        if (accountId != null) {
            Account acc = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));
            if (!acc.getCustomerId().equals(customerId)) {
                throw new AccountNotFoundException("Account does not belong to customer: " + accountId);
            }
            return acc;
        }

        if (accountNumber != null && !accountNumber.isBlank()) {
            Account acc = accountRepository.findByAccountNumber(accountNumber.trim())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));
            if (!acc.getCustomerId().equals(customerId)) {
                throw new AccountNotFoundException("Account does not belong to customer: " + accountNumber);
            }
            return acc;
        }

        // Default to first active savings account or checking account
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        if (accounts.isEmpty()) {
            throw new AccountNotFoundException("No active bank accounts found for customer ID: " + customerId);
        }

        return accounts.stream()
                .filter(a -> "SAVINGS".equalsIgnoreCase(a.getAccountType()) && "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .findFirst()
                .orElse(accounts.get(0));
    }

    private String generateUniqueVaultReference() {
        return "VAULT-" + (10000 + random.nextInt(90000));
    }

    private VaultResponse mapToVaultResponse(AccountVault v) {
        double target = v.getTargetAmount() != null ? v.getTargetAmount().doubleValue() : 0.0;
        double current = v.getCurrentAmount() != null ? v.getCurrentAmount().doubleValue() : 0.0;
        double progress = target > 0 ? Math.min(1.0, Math.max(0.0, current / target)) : 0.0;
        boolean isCompleted = current >= (target - 0.005);
        BigDecimal remaining = BigDecimal.valueOf(Math.max(0.0, target - current)).setScale(4, RoundingMode.HALF_UP);

        return VaultResponse.builder()
                .id(v.getVaultReference() != null ? v.getVaultReference() : "VAULT-" + v.getVaultId())
                .vaultId(v.getVaultId())
                .vaultReference(v.getVaultReference())
                .customerId(v.getCustomerId())
                .accountId(v.getAccount() != null ? v.getAccount().getAccountId() : null)
                .accountNumber(v.getAccount() != null ? v.getAccount().getAccountNumber() : null)
                .accountType(v.getAccount() != null ? v.getAccount().getAccountType() : null)
                .name(v.getName())
                .targetAmount(v.getTargetAmount())
                .currentAmount(v.getCurrentAmount())
                .targetDate(v.getTargetDate())
                .category(v.getCategory())
                .iconCode(v.getIconCode())
                .colorValue(v.getColorValue())
                .progress(progress)
                .isCompleted(isCompleted)
                .remaining(remaining)
                .status(v.getStatus())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
