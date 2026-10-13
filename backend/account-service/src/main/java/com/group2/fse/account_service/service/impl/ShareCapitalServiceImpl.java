package com.group2.fse.account_service.service.impl;

import com.group2.fse.account_service.dto.sharecapital.*;
import com.group2.fse.account_service.entity.*;
import com.group2.fse.account_service.exception.AccountNotFoundException;
import com.group2.fse.account_service.exception.InsufficientAccountFundsException;
import com.group2.fse.account_service.repository.*;
import com.group2.fse.account_service.service.ShareCapitalService;
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
public class ShareCapitalServiceImpl implements ShareCapitalService {

    private final MemberShareCapitalRepository memberShareCapitalRepository;
    private final ShareCapitalTransactionRepository shareCapitalTransactionRepository;
    private final PatronageDividendPayoutRepository patronageDividendPayoutRepository;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final Random random = new SecureRandom();

    @Override
    @Transactional
    public ShareCapitalResponse getMemberShareCapital(Long customerId) {
        log.info("Fetching share capital portfolio for customer ID {}", customerId);
        MemberShareCapital shareCapital = getOrCreateShareCapital(customerId);
        return mapToResponse(shareCapital);
    }

    @Override
    @Transactional
    public ShareCapitalTopUpResponse topUpShareCapital(Long customerId, ShareCapitalTopUpRequest request) {
        log.info("Processing share capital top-up of {} for customer ID {}", request.getAmount(), customerId);

        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be strictly greater than zero");
        }

        MemberShareCapital shareCapital = getOrCreateShareCapital(customerId);
        Account sourceAccount = resolveSourceAccount(customerId, request.getSourceAccountId(), request.getSourceAccountNumber());

        Balance balance = balanceRepository.findByAccount_AccountId(sourceAccount.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Balance not found for source account: " + sourceAccount.getAccountNumber()));

        if (balance.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientAccountFundsException(String.format(
                    "Insufficient funds in %s for share capital top-up. Available: %.2f, Required: %.2f",
                    sourceAccount.getAccountNumber(), balance.getAvailableBalance().doubleValue(), amount.doubleValue()));
        }

        // Deduct from source account balance
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(amount));
        balanceRepository.save(balance);

        BigDecimal parValue = shareCapital.getParValue();
        int sharesAdded = (int) Math.floor(amount.doubleValue() / parValue.doubleValue());
        if (sharesAdded < 1 && amount.compareTo(parValue) < 0) {
            log.info("Top-up amount {} is fractional relative to par value {}, crediting amount to paid balance", amount, parValue);
        }

        int newPaidUpShares = shareCapital.getPaidUpShares() + Math.max(0, sharesAdded);
        BigDecimal newTotalPaidAmount = shareCapital.getTotalPaidAmount().add(amount);

        // Auto-expand subscribed shares if member surpasses their current pledge
        int newSubscribedShares = Math.max(shareCapital.getSubscribedShares(), newPaidUpShares);

        shareCapital.setPaidUpShares(newPaidUpShares);
        shareCapital.setSubscribedShares(newSubscribedShares);
        shareCapital.setTotalPaidAmount(newTotalPaidAmount);
        shareCapital.setHasVotingRights(newPaidUpShares >= shareCapital.getMinimumRequiredShares());
        shareCapital.setMembershipType(shareCapital.getHasVotingRights() ? "REGULAR" : "ASSOCIATE");
        shareCapital.setUpdatedAt(LocalDateTime.now());

        MemberShareCapital saved = memberShareCapitalRepository.save(shareCapital);

        String refNo = "SC-TOP-" + (10000 + random.nextInt(90000));
        ShareCapitalTransaction tx = ShareCapitalTransaction.builder()
                .memberShareCapital(saved)
                .transactionType("TOP_UP")
                .amount(amount)
                .sharesAdded(sharesAdded)
                .balanceAfter(newTotalPaidAmount)
                .sourceAccountId(sourceAccount.getAccountId())
                .referenceNo(refNo)
                .createdAt(LocalDateTime.now())
                .build();
        shareCapitalTransactionRepository.save(tx);

        log.info("Successfully added {} shares (₱{}) to certificate {} for customer {}. New paid-up shares: {}",
                sharesAdded, amount, saved.getCertificateNo(), customerId, newPaidUpShares);

        return ShareCapitalTopUpResponse.builder()
                .referenceNo(refNo)
                .amountPaid(amount)
                .sharesAdded(sharesAdded)
                .newPaidUpShares(newPaidUpShares)
                .newTotalPaidAmount(newTotalPaidAmount)
                .hasVotingRights(saved.getHasVotingRights())
                .membershipType(saved.getMembershipType())
                .sourceAccountAvailableBalance(balance.getAvailableBalance())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public List<DividendPayoutResponse> getDividendHistory(Long customerId) {
        log.info("Fetching dividend history for customer ID {}", customerId);
        getOrCreateShareCapital(customerId);
        List<PatronageDividendPayout> payouts = patronageDividendPayoutRepository.findByCustomerIdOrderByFiscalYearDesc(customerId);
        return payouts.stream()
                .map(this::mapDividendResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<ShareCapitalTransactionResponse> getTransactions(Long customerId) {
        log.info("Fetching share capital transaction log for customer ID {}", customerId);
        MemberShareCapital shareCapital = getOrCreateShareCapital(customerId);
        List<ShareCapitalTransaction> txs = shareCapitalTransactionRepository
                .findByMemberShareCapital_ShareCapitalIdOrderByCreatedAtDesc(shareCapital.getShareCapitalId());
        return txs.stream()
                .map(this::mapTxResponse)
                .toList();
    }

    private MemberShareCapital getOrCreateShareCapital(Long customerId) {
        return memberShareCapitalRepository.findByCustomerId(customerId)
                .orElseGet(() -> provisionInitialShareCapital(customerId));
    }

    private MemberShareCapital provisionInitialShareCapital(Long customerId) {
        log.info("Auto-provisioning initial cooperative share capital ledger for customer ID {}", customerId);

        Account account = resolveDefaultAccount(customerId);
        String certNo = "SC-2024-" + String.format("%06d", customerId != null ? customerId : 1001L);

        MemberShareCapital shareCapital = MemberShareCapital.builder()
                .certificateNo(certNo)
                .customerId(customerId)
                .account(account)
                .subscribedShares(500)
                .paidUpShares(250)
                .parValue(BigDecimal.valueOf(100).setScale(4))
                .totalPaidAmount(BigDecimal.valueOf(25000).setScale(4))
                .minimumRequiredShares(20)
                .dividendRate(BigDecimal.valueOf(7.25))
                .membershipType("REGULAR")
                .hasVotingRights(true)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusMonths(14))
                .updatedAt(LocalDateTime.now())
                .build();

        MemberShareCapital saved = memberShareCapitalRepository.save(shareCapital);

        // Seed initial subscription audit transaction
        ShareCapitalTransaction initialTx = ShareCapitalTransaction.builder()
                .memberShareCapital(saved)
                .transactionType("INITIAL_SUBSCRIPTION")
                .amount(BigDecimal.valueOf(25000).setScale(4))
                .sharesAdded(250)
                .balanceAfter(BigDecimal.valueOf(25000).setScale(4))
                .sourceAccountId(account.getAccountId())
                .referenceNo("SC-INIT-001")
                .createdAt(LocalDateTime.now().minusMonths(14))
                .build();
        shareCapitalTransactionRepository.save(initialTx);

        // Seed mock dividend and patronage payouts for past 2 fiscal years
        PatronageDividendPayout div2025 = PatronageDividendPayout.builder()
                .memberShareCapital(saved)
                .customerId(customerId)
                .fiscalYear(2025)
                .shareCapitalDividend(BigDecimal.valueOf(1812.50).setScale(4))
                .patronageRefund(BigDecimal.valueOf(940.00).setScale(4))
                .totalPayout(BigDecimal.valueOf(2752.50).setScale(4))
                .disposition("CREDITED_TO_SAVINGS")
                .payoutDate(LocalDateTime.of(2026, 3, 31, 10, 0))
                .referenceNo("DIV-2025-" + (1000 + random.nextInt(9000)))
                .status("PAID")
                .createdAt(LocalDateTime.of(2026, 3, 31, 10, 0))
                .build();
        patronageDividendPayoutRepository.save(div2025);

        PatronageDividendPayout div2024 = PatronageDividendPayout.builder()
                .memberShareCapital(saved)
                .customerId(customerId)
                .fiscalYear(2024)
                .shareCapitalDividend(BigDecimal.valueOf(1500.00).setScale(4))
                .patronageRefund(BigDecimal.valueOf(750.00).setScale(4))
                .totalPayout(BigDecimal.valueOf(2250.00).setScale(4))
                .disposition("CREDITED_TO_SAVINGS")
                .payoutDate(LocalDateTime.of(2025, 3, 31, 10, 0))
                .referenceNo("DIV-2024-" + (1000 + random.nextInt(9000)))
                .status("PAID")
                .createdAt(LocalDateTime.of(2025, 3, 31, 10, 0))
                .build();
        patronageDividendPayoutRepository.save(div2024);

        log.info("Provisioned share capital certificate {} with 2 historical dividend payouts", certNo);
        return saved;
    }

    private Account resolveSourceAccount(Long customerId, Long accountId, String accountNumber) {
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

        return resolveDefaultAccount(customerId);
    }

    private Account resolveDefaultAccount(Long customerId) {
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        if (accounts.isEmpty()) {
            throw new AccountNotFoundException("No bank accounts found for customer ID: " + customerId);
        }

        return accounts.stream()
                .filter(a -> "SAVINGS".equalsIgnoreCase(a.getAccountType()) && "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .findFirst()
                .orElse(accounts.get(0));
    }

    private ShareCapitalResponse mapToResponse(MemberShareCapital sc) {
        BigDecimal parValue = sc.getParValue() != null ? sc.getParValue() : BigDecimal.valueOf(100);
        BigDecimal subscribedAmount = parValue.multiply(BigDecimal.valueOf(sc.getSubscribedShares()));
        double totalPaid = sc.getTotalPaidAmount() != null ? sc.getTotalPaidAmount().doubleValue() : 0.0;
        double subAmount = subscribedAmount.doubleValue();
        double ratio = subAmount > 0 ? Math.min(1.0, Math.max(0.0, totalPaid / subAmount)) : 0.0;

        return ShareCapitalResponse.builder()
                .shareCapitalId(sc.getShareCapitalId())
                .certificateNo(sc.getCertificateNo())
                .customerId(sc.getCustomerId())
                .accountId(sc.getAccount() != null ? sc.getAccount().getAccountId() : null)
                .accountNumber(sc.getAccount() != null ? sc.getAccount().getAccountNumber() : null)
                .subscribedShares(sc.getSubscribedShares())
                .paidUpShares(sc.getPaidUpShares())
                .parValue(parValue)
                .totalPaidAmount(sc.getTotalPaidAmount())
                .subscribedAmount(subscribedAmount)
                .minimumRequiredShares(sc.getMinimumRequiredShares())
                .dividendRate(sc.getDividendRate())
                .membershipType(sc.getMembershipType())
                .hasVotingRights(sc.getHasVotingRights())
                .completionRatio(ratio)
                .status(sc.getStatus())
                .createdAt(sc.getCreatedAt())
                .updatedAt(sc.getUpdatedAt())
                .build();
    }

    private DividendPayoutResponse mapDividendResponse(PatronageDividendPayout p) {
        return DividendPayoutResponse.builder()
                .dividendId(p.getDividendId())
                .fiscalYear(p.getFiscalYear())
                .shareCapitalDividend(p.getShareCapitalDividend())
                .patronageRefund(p.getPatronageRefund())
                .totalPayout(p.getTotalPayout())
                .disposition(p.getDisposition())
                .payoutDate(p.getPayoutDate())
                .referenceNo(p.getReferenceNo())
                .status(p.getStatus())
                .build();
    }

    private ShareCapitalTransactionResponse mapTxResponse(ShareCapitalTransaction tx) {
        return ShareCapitalTransactionResponse.builder()
                .txId(tx.getTxId())
                .transactionType(tx.getTransactionType())
                .amount(tx.getAmount())
                .sharesAdded(tx.getSharesAdded())
                .balanceAfter(tx.getBalanceAfter())
                .referenceNo(tx.getReferenceNo())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
