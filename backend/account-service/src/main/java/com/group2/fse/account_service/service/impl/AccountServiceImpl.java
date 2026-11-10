package com.group2.fse.account_service.service.impl;

import com.group2.fse.account_service.dto.AccountResponse;
import com.group2.fse.account_service.dto.CreateAccountRequest;
import com.group2.fse.account_service.entity.Account;
import com.group2.fse.account_service.entity.Balance;
import com.group2.fse.account_service.event.AccountCreatedEvent;
import com.group2.fse.account_service.event.AccountEventPublisher;
import com.group2.fse.account_service.exception.AccountNotFoundException;
import com.group2.fse.account_service.exception.UnauthorizedAccountAccessException;
import com.group2.fse.account_service.repository.AccountRepository;
import com.group2.fse.account_service.repository.BalanceRepository;
import com.group2.fse.account_service.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final AccountEventPublisher accountEventPublisher;
    private final Random random = new SecureRandom();

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String accountNumber = generateUniqueAccountNumber();

        Account account = Account.builder()
                .customerId(request.getCustomerId())
                .accountNumber(accountNumber)
                .accountType(request.getAccountType().toUpperCase())
                .currency(request.getCurrency() != null ? request.getCurrency().toUpperCase() : "PHP")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        Account savedAccount = accountRepository.save(account);

        Balance balance = Balance.builder()
                .account(savedAccount)
                .availableBalance(BigDecimal.ZERO.setScale(4))
                .updatedAt(LocalDateTime.now())
                .build();

        balanceRepository.save(balance);
        savedAccount.setBalance(balance);

        log.info("Opened new {} account {} for customer ID {}",
                savedAccount.getAccountType(), savedAccount.getAccountNumber(), request.getCustomerId());

        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .accountId(savedAccount.getAccountId())
                .accountNumber(savedAccount.getAccountNumber())
                .accountType(savedAccount.getAccountType())
                .customerId(request.getCustomerId())
                .currency(savedAccount.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();
        accountEventPublisher.publishAccountCreated(event);

        return mapToAccountResponse(savedAccount, balance);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getCustomerAccounts(Long customerId) {
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        return accounts.stream()
                .map(acc -> {
                    Balance balance = balanceRepository.findByAccount_AccountId(acc.getAccountId()).orElse(null);
                    return mapToAccountResponse(acc, balance);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(acc -> {
                    Balance balance = balanceRepository.findByAccount_AccountId(acc.getAccountId()).orElse(null);
                    return mapToAccountResponse(acc, balance);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId, Long customerId, boolean isStaff) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));

        if (!isStaff) {
            if (customerId == null || !account.getCustomerId().equals(customerId)) {
                throw new UnauthorizedAccountAccessException("You do not own this account and cannot access its details.");
            }
        }

        Balance balance = balanceRepository.findByAccount_AccountId(accountId).orElse(null);
        return mapToAccountResponse(account, balance);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with account number: " + accountNumber));
        Balance balance = balanceRepository.findByAccount_AccountId(account.getAccountId()).orElse(null);
        return mapToAccountResponse(account, balance);
    }

    @Override
    @Transactional
    public AccountResponse updateAccountStatus(Long accountId, String status) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));

        String newStatus = status.toUpperCase().trim();
        account.setStatus(newStatus);
        Account saved = accountRepository.save(account);

        log.info("Updated account {} status to {}", account.getAccountNumber(), newStatus);

        Balance balance = balanceRepository.findByAccount_AccountId(accountId).orElse(null);
        return mapToAccountResponse(saved, balance);
    }

    private String generateUniqueAccountNumber() {
        for (int i = 0; i < 20; i++) {
            int seq = 10000000 + random.nextInt(90000000);
            String candidate = "ACC_" + seq;
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        return "ACC_" + System.currentTimeMillis();
    }

    private AccountResponse mapToAccountResponse(Account account, Balance balance) {
        BigDecimal availBal = balance != null && balance.getAvailableBalance() != null
                ? balance.getAvailableBalance() : BigDecimal.ZERO.setScale(4);

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .customerId(account.getCustomerId())
                .customerName("Customer #" + account.getCustomerId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .availableBalance(availBal)
                .createdAt(account.getCreatedAt())
                .build();
    }
}
