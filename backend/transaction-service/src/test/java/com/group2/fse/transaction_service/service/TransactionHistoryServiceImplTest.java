package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.SpendingBreakdownDto;
import com.group2.fse.transaction_service.dto.TransactionSummaryDto;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.service.impl.TransactionHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionHistoryServiceImpl Unit Tests")
class TransactionHistoryServiceImplTest {

    @Mock
    private TxnTransactionRepository transactionRepository;

    @InjectMocks
    private TransactionHistoryServiceImpl historyService;

    private TxnTransaction txn1;
    private TxnTransaction txn2;
    private TxnTransaction txn3;

    @BeforeEach
    void setUp() {
        txn1 = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("TXN-20261007-0001")
                .customerId(101L)
                .amount(new BigDecimal("1500.00"))
                .currency("PHP")
                .category("UTILITIES")
                .status("COMPLETED")
                .destinationAccountNumber("ACC-2001")
                .remarks("Electric Bill")
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        txn2 = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("TXN-20261007-0002")
                .customerId(101L)
                .amount(new BigDecimal("500.00"))
                .currency("PHP")
                .category("FOOD")
                .status("COMPLETED")
                .destinationAccountNumber("ACC-2002")
                .remarks("Lunch")
                .createdAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();

        txn3 = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("TXN-20261007-0003")
                .customerId(101L)
                .amount(new BigDecimal("1000.00"))
                .currency("PHP")
                .category("FOOD")
                .status("FAILED")
                .destinationAccountNumber("ACC-2003")
                .remarks("Failed Lunch")
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("getRecentTransactions applies limit and maps fields correctly")
    void getRecentTransactions_appliesLimitAndMaps() {
        when(transactionRepository.findByCustomerIdOrderByCreatedAtDesc(101L))
                .thenReturn(List.of(txn1, txn2, txn3));

        List<TransactionSummaryDto> result = historyService.getRecentTransactions(101L, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReferenceNo()).isEqualTo("TXN-20261007-0001");
        assertThat(result.get(0).getAmount()).isEqualTo("1500.00");
        assertThat(result.get(0).getCategory()).isEqualTo("UTILITIES");
        assertThat(result.get(0).getCounterpartyName()).isEqualTo("Electric Bill");
        assertThat(result.get(1).getReferenceNo()).isEqualTo("TXN-20261007-0002");
        assertThat(result.get(1).getAmount()).isEqualTo("500.00");

        verify(transactionRepository, times(1)).findByCustomerIdOrderByCreatedAtDesc(101L);
    }

    @Test
    @DisplayName("getRecentTransactions defaults to 5 when limit <= 0")
    void getRecentTransactions_defaultLimit() {
        when(transactionRepository.findByCustomerIdOrderByCreatedAtDesc(101L))
                .thenReturn(List.of(txn1, txn2));

        List<TransactionSummaryDto> result = historyService.getRecentTransactions(101L, 0);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getSpendingBreakdown aggregates categories and filters out FAILED transactions")
    void getSpendingBreakdown_aggregatesAndFiltersFailed() {
        TxnTransaction food2 = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("TXN-20261007-0004")
                .customerId(101L)
                .amount(new BigDecimal("750.50"))
                .currency("PHP")
                .category("FOOD")
                .status("COMPLETED")
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();

        TxnTransaction oldTxn = TxnTransaction.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("TXN-20261007-0005")
                .customerId(101L)
                .amount(new BigDecimal("9999.00"))
                .currency("PHP")
                .category("SHOPPING")
                .status("COMPLETED")
                .createdAt(Instant.now().minus(40, ChronoUnit.DAYS))
                .build();

        when(transactionRepository.findByCustomerIdOrderByCreatedAtDesc(101L))
                .thenReturn(List.of(txn1, txn2, txn3, food2, oldTxn));

        SpendingBreakdownDto breakdown = historyService.getSpendingBreakdown(101L, 30);

        assertThat(breakdown.getPeriodDays()).isEqualTo(30);
        // Should contain UTILITIES (1500.00) and FOOD (500.00 + 750.50 = 1250.50). Failed and old transactions excluded.
        assertThat(breakdown.getItems()).hasSize(2);

        SpendingBreakdownDto.SpendingItemDto foodItem = breakdown.getItems().stream()
                .filter(i -> "FOOD".equals(i.getCategory()))
                .findFirst()
                .orElse(null);
        assertThat(foodItem).isNotNull();
        assertThat(foodItem.getCount()).isEqualTo(2);
        assertThat(foodItem.getAmount()).isEqualTo("1250.50");

        SpendingBreakdownDto.SpendingItemDto utilItem = breakdown.getItems().stream()
                .filter(i -> "UTILITIES".equals(i.getCategory()))
                .findFirst()
                .orElse(null);
        assertThat(utilItem).isNotNull();
        assertThat(utilItem.getCount()).isEqualTo(1);
        assertThat(utilItem.getAmount()).isEqualTo("1500.00");
    }
}
