package com.group2.fse.account_service.service.impl;

import com.group2.fse.account_service.dto.AccountFlagRequest;
import com.group2.fse.account_service.dto.AccountFlagResponse;
import com.group2.fse.account_service.entity.Account;
import com.group2.fse.account_service.entity.AccountFlag;
import com.group2.fse.account_service.exception.AccountNotFoundException;
import com.group2.fse.account_service.repository.AccountFlagRepository;
import com.group2.fse.account_service.repository.AccountRepository;
import com.group2.fse.account_service.service.AccountFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountFlagServiceImpl implements AccountFlagService {

    private final AccountRepository accountRepository;
    private final AccountFlagRepository accountFlagRepository;

    @Override
    @Transactional
    public AccountFlagResponse addFlag(Long accountId, AccountFlagRequest request, Long staffUserId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));

        AccountFlag flag = AccountFlag.builder()
                .account(account)
                .reason(request.getReason())
                .status("ACTIVE")
                .flaggedBy(staffUserId)
                .flaggedAt(LocalDateTime.now())
                .build();

        AccountFlag saved = accountFlagRepository.save(flag);
        log.info("Imposed administrative hold on account {} (Flag ID: {}) by user ID {}",
                account.getAccountNumber(), saved.getFlagId(), staffUserId);

        return AccountFlagResponse.builder()
                .flagId(saved.getFlagId())
                .accountId(account.getAccountId())
                .reason(saved.getReason())
                .status("ACTIVE")
                .flaggedBy(staffUserId)
                .flaggedAt(saved.getFlaggedAt())
                .message("Administrative hold imposed successfully.")
                .build();
    }

    @Override
    @Transactional
    public AccountFlagResponse removeFlag(Long accountId, Long flagId, Long adminUserId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));

        AccountFlag flag = accountFlagRepository.findById(flagId)
                .orElseThrow(() -> new IllegalArgumentException("Account flag not found with ID: " + flagId));

        if (!flag.getAccount().getAccountId().equals(accountId)) {
            throw new IllegalArgumentException("Flag ID " + flagId + " does not belong to account ID " + accountId);
        }

        if (!"ACTIVE".equalsIgnoreCase(flag.getStatus())) {
            throw new IllegalStateException("Flag ID " + flagId + " is not ACTIVE (current: " + flag.getStatus() + ")");
        }

        flag.setStatus("REMOVED");
        flag.setRemovedBy(adminUserId);
        flag.setRemovedAt(LocalDateTime.now());
        accountFlagRepository.save(flag);

        log.info("Lifted administrative hold (Flag ID: {}) from account {} by admin user ID {}",
                flagId, account.getAccountNumber(), adminUserId);

        return AccountFlagResponse.builder()
                .flagId(flag.getFlagId())
                .accountId(account.getAccountId())
                .reason(flag.getReason())
                .status("REMOVED")
                .flaggedBy(flag.getFlaggedBy())
                .flaggedAt(flag.getFlaggedAt())
                .removedBy(adminUserId)
                .removedAt(flag.getRemovedAt())
                .message("Administrative hold lifted successfully.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountFlagResponse> getFlagsForAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException("Account not found with ID: " + accountId);
        }

        return accountFlagRepository.findByAccount_AccountId(accountId).stream()
                .map(flag -> AccountFlagResponse.builder()
                        .flagId(flag.getFlagId())
                        .accountId(accountId)
                        .reason(flag.getReason())
                        .status(flag.getStatus())
                        .flaggedBy(flag.getFlaggedBy())
                        .flaggedAt(flag.getFlaggedAt())
                        .removedBy(flag.getRemovedBy())
                        .removedAt(flag.getRemovedAt())
                        .build())
                .toList();
    }
}
