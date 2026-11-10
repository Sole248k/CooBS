package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.SpendingBreakdownDto;
import com.group2.fse.transaction_service.dto.TransactionSummaryDto;

import java.util.List;

public interface TransactionHistoryService {
    List<TransactionSummaryDto> getRecentTransactions(Long customerId, int limit);
    SpendingBreakdownDto getSpendingBreakdown(Long customerId, int days);
}
