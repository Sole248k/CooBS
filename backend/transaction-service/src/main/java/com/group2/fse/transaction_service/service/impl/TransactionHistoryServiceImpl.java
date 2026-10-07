package com.group2.fse.transaction_service.service.impl;

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

    @Override
    @Transactional(readOnly = true)
    public List<TransactionSummaryDto> getRecentTransactions(Long customerId, int limit) {
        log.info("Fetching recent transactions for customerId={}, limit={}", customerId, limit);
        List<TxnTransaction> transactions = transactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);

        int max = limit > 0 ? limit : 5;
        return transactions.stream()
                .limit(max)
                .map(this::mapToSummary)
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

    private TransactionSummaryDto mapToSummary(TxnTransaction txn) {
        String amountStr = txn.getAmount() != null ? txn.getAmount().setScale(2).toString() : "0.00";
        String createdAtStr = txn.getCreatedAt() != null ? txn.getCreatedAt().toString() : Instant.now().toString();

        return TransactionSummaryDto.builder()
                .transactionId(txn.getTransactionId())
                .referenceNo(txn.getReferenceNo())
                .direction("OUT")
                .counterpartyName(txn.getRemarks())
                .counterpartyAccountNumber(txn.getDestinationAccountNumber())
                .amount(amountStr)
                .currency(txn.getCurrency() != null ? txn.getCurrency() : "PHP")
                .category(txn.getCategory() != null ? txn.getCategory() : "TRANSFER")
                .status(txn.getStatus())
                .createdAt(createdAtStr)
                .build();
    }
}
