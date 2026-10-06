package com.group2.fse.account_service.service.impl;

import com.group2.fse.account_service.dto.AccountClosureRequestDto;
import com.group2.fse.account_service.dto.ClosureRequestResponse;
import com.group2.fse.account_service.dto.PageResponse;
import com.group2.fse.account_service.entity.*;
import com.group2.fse.account_service.event.AccountClosureCompletedEvent;
import com.group2.fse.account_service.event.AccountEventPublisher;
import com.group2.fse.account_service.exception.*;
import com.group2.fse.account_service.repository.*;
import com.group2.fse.account_service.service.AccountClosureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountClosureServiceImpl implements AccountClosureService {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final AccountClosureRequestRepository closureRequestRepository;
    private final AccountFlagRepository accountFlagRepository;
    private final AccountEventPublisher accountEventPublisher;

    @Override
    @Transactional
    public ClosureRequestResponse submitClosureRequest(Long accountId, Long customerId, AccountClosureRequestDto request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));

        // 1. Ownership check
        if (customerId == null || !account.getCustomerId().equals(customerId)) {
            throw new UnauthorizedAccountAccessException("You do not own this account and cannot request its closure.");
        }

        // 2. Account status check
        if ("CLOSED".equalsIgnoreCase(account.getStatus())) {
            throw new AccountNotActiveException("Account " + account.getAccountNumber() + " is already closed.");
        }

        // 3. Duplicate pending request check
        if (closureRequestRepository.existsByAccount_AccountIdAndStatus(accountId, "PENDING")) {
            throw new DuplicateClosureRequestException("A pending closure request already exists for account " + account.getAccountNumber());
        }

        // 4. Active holds / flags check
        if (accountFlagRepository.existsByAccount_AccountIdAndStatus(accountId, "ACTIVE")) {
            throw new AccountActiveFlagException("Operation blocked by active risk/judicial hold on account " + account.getAccountNumber());
        }

        // 5. Zero-balance invariant check
        Balance balance = balanceRepository.findByAccount_AccountId(accountId).orElse(null);
        BigDecimal availBal = (balance != null && balance.getAvailableBalance() != null)
                ? balance.getAvailableBalance() : BigDecimal.ZERO.setScale(4);

        if (availBal.compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountNonZeroBalanceException(account.getAccountNumber(), availBal);
        }

        AccountClosureRequest closureRequest = AccountClosureRequest.builder()
                .account(account)
                .reason(request != null ? request.getReason() : null)
                .status("PENDING")
                .requestedAt(LocalDateTime.now())
                .build();

        AccountClosureRequest saved = closureRequestRepository.save(closureRequest);
        log.info("Account closure request #{} submitted for account {} by customer {}",
                saved.getClosureRequestId(), account.getAccountNumber(), customerId);

        return ClosureRequestResponse.builder()
                .closureRequestId(saved.getClosureRequestId())
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .customerId(customerId)
                .customerName("Customer #" + customerId)
                .availableBalance(availBal)
                .reason(saved.getReason())
                .status("PENDING")
                .requestedAt(saved.getRequestedAt())
                .message("Account closure request submitted for bank review. Account is pending closure.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClosureRequestResponse getClosureRequestForAccount(Long accountId, Long customerId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));

        if (customerId != null && !account.getCustomerId().equals(customerId)) {
            throw new UnauthorizedAccountAccessException("You do not own this account.");
        }

        AccountClosureRequest req = closureRequestRepository.findTopByAccount_AccountIdOrderByRequestedAtDesc(accountId)
                .orElseThrow(() -> new ClosureRequestNotFoundException("No closure request found for account ID: " + accountId));

        Balance balance = balanceRepository.findByAccount_AccountId(accountId).orElse(null);
        BigDecimal availBal = (balance != null && balance.getAvailableBalance() != null)
                ? balance.getAvailableBalance() : BigDecimal.ZERO.setScale(4);

        return mapToClosureResponse(req, availBal);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ClosureRequestResponse> getPendingClosureRequests(String status, Pageable pageable) {
        String filterStatus = (status == null || status.isBlank()) ? "PENDING" : status.toUpperCase();
        Page<AccountClosureRequest> page = closureRequestRepository.findByStatus(filterStatus, pageable);

        Page<ClosureRequestResponse> responsePage = page.map(req -> {
            Balance balance = balanceRepository.findByAccount_AccountId(req.getAccount().getAccountId()).orElse(null);
            BigDecimal availBal = (balance != null && balance.getAvailableBalance() != null)
                    ? balance.getAvailableBalance() : BigDecimal.ZERO.setScale(4);
            return mapToClosureResponse(req, availBal);
        });

        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional
    public ClosureRequestResponse approveClosureRequest(Long requestId, Long adminUserId) {
        AccountClosureRequest req = closureRequestRepository.findById(requestId)
                .orElseThrow(() -> new ClosureRequestNotFoundException("Closure request not found: " + requestId));

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalStateException("Closure request is not PENDING (current: " + req.getStatus() + ")");
        }

        Account account = req.getAccount();
        Balance balance = balanceRepository.findByAccount_AccountId(account.getAccountId()).orElse(null);
        BigDecimal availBal = (balance != null && balance.getAvailableBalance() != null)
                ? balance.getAvailableBalance() : BigDecimal.ZERO.setScale(4);

        if (availBal.compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountNonZeroBalanceException(account.getAccountNumber(), availBal);
        }

        // Transition account status to CLOSED
        account.setStatus("CLOSED");
        accountRepository.save(account);

        // Update request status to APPROVED
        req.setStatus("APPROVED");
        req.setApprovedBy(adminUserId);
        req.setReviewedAt(LocalDateTime.now());
        closureRequestRepository.save(req);

        log.info("Admin ID {} APPROVED closure request #{} for account {}. Customer profile {} remains ACTIVE.",
                adminUserId, req.getClosureRequestId(), account.getAccountNumber(), account.getCustomerId());

        // Emit domain event to Kafka
        AccountClosureCompletedEvent event = AccountClosureCompletedEvent.builder()
                .closureRequestId(req.getClosureRequestId())
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .customerId(account.getCustomerId())
                .approvedBy(adminUserId)
                .timestamp(LocalDateTime.now())
                .build();
        accountEventPublisher.publishAccountClosure(event);

        return ClosureRequestResponse.builder()
                .closureRequestId(req.getClosureRequestId())
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .accountStatus("CLOSED")
                .customerStatus("ACTIVE")
                .approvedBy(adminUserId)
                .reviewedAt(req.getReviewedAt())
                .message(String.format("Account %s has been closed successfully. Customer profile remains active.", account.getAccountNumber()))
                .build();
    }

    @Override
    @Transactional
    public ClosureRequestResponse rejectClosureRequest(Long requestId, Long adminUserId, String rejectionReason) {
        AccountClosureRequest req = closureRequestRepository.findById(requestId)
                .orElseThrow(() -> new ClosureRequestNotFoundException("Closure request not found: " + requestId));

        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalStateException("Closure request is not PENDING (current: " + req.getStatus() + ")");
        }

        req.setStatus("REJECTED");
        req.setRejectionReason(rejectionReason);
        req.setApprovedBy(adminUserId);
        req.setReviewedAt(LocalDateTime.now());
        closureRequestRepository.save(req);

        Account account = req.getAccount();
        log.info("Admin ID {} REJECTED closure request #{} for account {}. Reason: {}",
                adminUserId, req.getClosureRequestId(), account.getAccountNumber(), rejectionReason);

        return ClosureRequestResponse.builder()
                .closureRequestId(req.getClosureRequestId())
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .status("REJECTED")
                .rejectionReason(rejectionReason)
                .approvedBy(adminUserId)
                .reviewedAt(req.getReviewedAt())
                .message("Account closure request was rejected. Account remains active.")
                .build();
    }

    private ClosureRequestResponse mapToClosureResponse(AccountClosureRequest req, BigDecimal availBal) {
        Account acc = req.getAccount();
        Long custId = acc.getCustomerId();
        return ClosureRequestResponse.builder()
                .closureRequestId(req.getClosureRequestId())
                .accountId(acc.getAccountId())
                .accountNumber(acc.getAccountNumber())
                .accountType(acc.getAccountType())
                .customerId(custId)
                .customerName("Customer #" + custId)
                .availableBalance(availBal)
                .reason(req.getReason())
                .status(req.getStatus())
                .requestedAt(req.getRequestedAt())
                .reviewedAt(req.getReviewedAt())
                .approvedBy(req.getApprovedBy())
                .rejectionReason(req.getRejectionReason())
                .build();
    }
}
