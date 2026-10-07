package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.SpendingBreakdownDto;
import com.group2.fse.transaction_service.dto.TransactionSummaryDto;
import com.group2.fse.transaction_service.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal REST controller for T8/T9 transaction history aggregation (Handshake H10 for Customer 360).
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/transactions")
@RequiredArgsConstructor
public class HistoryInternalController {

    private final TransactionHistoryService transactionHistoryService;

    @GetMapping
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<List<TransactionSummaryDto>> getRecentTransactions(
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        log.info("Internal recent transactions lookup for customerId={}, limit={}", customerId, limit);
        List<TransactionSummaryDto> transactions = transactionHistoryService.getRecentTransactions(customerId, limit);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/spending")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<SpendingBreakdownDto> getSpendingBreakdown(
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        log.info("Internal spending breakdown lookup for customerId={}, days={}", customerId, days);
        SpendingBreakdownDto breakdown = transactionHistoryService.getSpendingBreakdown(customerId, days);
        return ResponseEntity.ok(breakdown);
    }
}
