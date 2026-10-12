package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.dto.SpendingBreakdownDto;
import com.group2.fse.transaction_service.dto.TransactionSummaryDto;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionHistoryServiceImpl implements TransactionHistoryService {

    private final TxnTransactionRepository transactionRepository;
    private final OrchestrationClient orchestrationClient;

    @Override
    @Transactional(readOnly = true)
    public List<TransactionSummaryDto> getRecentTransactions(Long customerId, int limit) {
        log.info("Fetching recent transactions for customerId={}, limit={}", customerId, limit);

        Set<Long> accountIds = Collections.emptySet();
        Set<String> accountNumbers = Collections.emptySet();

        if (orchestrationClient != null) {
            try {
                List<AccountLookupResponse> accounts = orchestrationClient.findAccountsByCustomerId(customerId);
                if (accounts != null && !accounts.isEmpty()) {
                    accountIds = accounts.stream()
                            .map(AccountLookupResponse::getAccountId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    accountNumbers = accounts.stream()
                            .map(AccountLookupResponse::getAccountNumber)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                }
            } catch (Exception ex) {
                log.warn("Could not retrieve accounts for customer {} during recent transactions lookup: {}", customerId, ex.getMessage());
            }
        }

        List<TxnTransaction> transactions;
        if (accountIds.isEmpty() && accountNumbers.isEmpty()) {
            transactions = transactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        } else {
            Set<Long> safeAccountIds = accountIds.isEmpty() ? Set.of(-1L) : accountIds;
            Set<String> safeAccountNumbers = accountNumbers.isEmpty() ? Set.of("__NONE__") : accountNumbers;
            transactions = transactionRepository.findByCustomerOrAccountDetails(customerId, safeAccountIds, safeAccountNumbers);
        }

        int max = limit > 0 ? limit : 25;
        final Set<Long> finalAccountIds = accountIds;
        final Set<String> finalAccountNumbers = accountNumbers;
        return transactions.stream()
                .limit(max)
                .map(txn -> mapToSummary(txn, customerId, finalAccountIds, finalAccountNumbers))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SpendingBreakdownDto getSpendingBreakdown(Long customerId, int days) {
        int periodDays = days > 0 ? days : 30;
        log.info("Calculating spending breakdown for customerId={}, periodDays={}", customerId, periodDays);

        Instant cutoff = Instant.now().minus(periodDays, ChronoUnit.DAYS);
        List<TxnTransaction> transactions = transactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);

        Map<String, List<TxnTransaction>> byCategory = transactions.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(cutoff))
                .filter(t -> !"FAILED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> !"REVERSAL".equalsIgnoreCase(t.getCategory()))
                .collect(Collectors.groupingBy(t -> t.getCategory() != null ? t.getCategory() : "TRANSFER"));

        List<SpendingBreakdownDto.SpendingItemDto> items = new ArrayList<>();
        for (Map.Entry<String, List<TxnTransaction>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<TxnTransaction> list = entry.getValue();

            BigDecimal total = list.stream()
                    .map(TxnTransaction::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            items.add(SpendingBreakdownDto.SpendingItemDto.builder()
                    .category(category)
                    .amount(total.setScale(2).toString())
                    .count(list.size())
                    .build());
        }

        return SpendingBreakdownDto.builder()
                .periodDays(periodDays)
                .items(items)
                .build();
    }

    private TransactionSummaryDto mapToSummary(TxnTransaction txn, Long customerId, Set<Long> customerAccountIds, Set<String> customerAccountNumbers) {
        String amountStr = txn.getAmount() != null ? txn.getAmount().setScale(2).toString() : "0.00";
        String createdAtStr = txn.getCreatedAt() != null ? txn.getCreatedAt().toString() : Instant.now().toString();

        boolean isReversal = txn.getReversesTransactionId() != null
                || "REVERSAL".equalsIgnoreCase(txn.getCategory())
                || (txn.getReferenceNo() != null && txn.getReferenceNo().startsWith("RV-"));

        boolean isDestination = (txn.getDestinationAccountId() != null && customerAccountIds.contains(txn.getDestinationAccountId()))
                || (txn.getDestinationAccountNumber() != null && customerAccountNumbers.contains(txn.getDestinationAccountNumber()));

        boolean isSource = (txn.getSourceAccountId() != null && customerAccountIds.contains(txn.getSourceAccountId()));

        String direction;
        String counterpartyName;
        String counterpartyAccountNumber;

        if (isReversal) {
            // In a reversal, destinationAccountId receives the refunded money.
            boolean isRefundRecipient = isDestination || (!isSource && (txn.getCustomerId() != null && txn.getCustomerId().equals(customerId)));
            if (isRefundRecipient) {
                direction = "IN";
                counterpartyName = txn.getRemarks() != null && !txn.getRemarks().isBlank() ? txn.getRemarks() : "Reversal Refund";
                counterpartyAccountNumber = txn.getSourceAccountId() != null ? "ACC_" + txn.getSourceAccountId() : "Internal Reversal";
            } else {
                direction = "OUT";
                counterpartyName = txn.getRemarks() != null && !txn.getRemarks().isBlank() ? txn.getRemarks() : "Reversal Debit";
                counterpartyAccountNumber = txn.getDestinationAccountId() != null ? "ACC_" + txn.getDestinationAccountId() : "Internal Reversal";
            }
        } else if (isDestination && !isSource) {
            direction = "IN";
            counterpartyName = txn.getRemarks() != null && !txn.getRemarks().isBlank() ? txn.getRemarks() : "Account Transfer";
            counterpartyAccountNumber = txn.getSourceAccountId() != null ? "ACC_" + txn.getSourceAccountId() : "External";
        } else if ("DEPOSIT".equalsIgnoreCase(txn.getCategory())) {
            direction = "IN";
            counterpartyName = txn.getRemarks() != null && !txn.getRemarks().isBlank() ? txn.getRemarks() : "Cash Deposit";
            counterpartyAccountNumber = txn.getDestinationAccountNumber();
        } else {
            direction = "OUT";
            counterpartyName = txn.getRemarks();
            counterpartyAccountNumber = txn.getDestinationAccountNumber();
        }

        return TransactionSummaryDto.builder()
                .transactionId(txn.getTransactionId())
                .referenceNo(txn.getReferenceNo())
                .direction(direction)
                .counterpartyName(counterpartyName)
                .counterpartyAccountNumber(counterpartyAccountNumber)
                .amount(amountStr)
                .currency(txn.getCurrency() != null ? txn.getCurrency() : "PHP")
                .category(isReversal ? "REVERSAL" : (txn.getCategory() != null ? txn.getCategory() : "TRANSFER"))
                .status(txn.getStatus())
                .failureCode(txn.getFailureCode())
                .createdAt(createdAtStr)
                .build();
    }
}
