package com.group2.fse.ledger_service.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.group2.fse.ledger_service.dto.MutationResult;
import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.exception.DualWriteAuditException;
import com.group2.fse.ledger_service.posting.PostingEngineImpl;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.service.impl.AccountBalanceServiceImpl;

@DisplayName("Core Balance Mutation & Dual-Write Ledger Service Tests (FSE-303, FSE-304, FSE-305)")
class AccountBalanceServiceTest {

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DualWriteLedgerAuditService dualWriteAuditService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    private AccountBalanceServiceImpl balanceService;

    private Account mockAccount;
    private Balance mockBalance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Real posting engine over the mocked repositories: the service no longer does its own balance math
        balanceService = new AccountBalanceServiceImpl(
                balanceRepository,
                new PostingEngineImpl(balanceRepository, transactionRepository, dualWriteAuditService),
                applicationEventPublisher);

        mockAccount = Account.builder()
                .accountId(1L)
                .accountNumber("ACC-10001")
                .currency("PHP")
                .status("ACTIVE")
                .build();

        mockBalance = Balance.builder()
                .balanceId(1L)
                .account(mockAccount)
                .availableBalance(new BigDecimal("1000.0000"))
                .ledgerBalance(new BigDecimal("1000.0000"))
                .build();
    }

    @Test
    @DisplayName("FSE-303 & FSE-304: Successful debit with simultaneous Oracle and PostgreSQL dual-write")
    void testExecuteDebit_Success() {
        when(balanceRepository.findByAccountId(1L)).thenReturn(Optional.of(mockBalance));

        Transaction savedTxn = Transaction.builder()
                .transactionId(1001L)
                .account(mockAccount)
                .referenceNo("REF-001")
                .transactionType("DEBIT")
                .amount(new BigDecimal("200.0000"))
                .previousBalance(new BigDecimal("1000.0000"))
                .newBalance(new BigDecimal("800.0000"))
                .status("COMPLETED")
                .build();

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTxn);

        MutationResult result = balanceService.executeDebit(1L, new BigDecimal("200.0000"), "REF-001");

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("1000.0000"), result.getPreviousBalance());
        assertEquals(new BigDecimal("800.0000"), result.getNewBalance());
        assertEquals(1001L, result.getTransactionId());

        // Verify Oracle balance update
        verify(balanceRepository, times(1)).save(mockBalance);

        // Verify Oracle transaction journal entry
        verify(transactionRepository, times(1)).save(any(Transaction.class));

        // Verify PostgreSQL dual-write audit update
        verify(dualWriteAuditService, times(1)).recordMutationAudit(
                eq(1001L),
                eq(1L),
                eq("DEBIT"),
                eq(new BigDecimal("200.0000")),
                eq(new BigDecimal("1000.0000")),
                eq(new BigDecimal("800.0000")),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("FSE-303: Invariant check - debit fails when balance is insufficient")
    void testExecuteDebit_InsufficientBalance() {
        mockBalance.setAvailableBalance(new BigDecimal("100.0000"));
        when(balanceRepository.findByAccountId(1L)).thenReturn(Optional.of(mockBalance));

        MutationResult result = balanceService.executeDebit(1L, new BigDecimal("200.0000"), "REF-002");

        assertFalse(result.isSuccess());
        assertEquals("Insufficient funds for debit operation.", result.getErrorMessage());
        verify(balanceRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(dualWriteAuditService, never()).recordMutationAudit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("FSE-303: Invariant check - debit fails when amount is zero or negative")
    void testExecuteDebit_InvalidAmount() {
        MutationResult zeroResult = balanceService.executeDebit(1L, BigDecimal.ZERO, "REF-003");
        assertFalse(zeroResult.isSuccess());
        assertEquals("Debit amount must be greater than zero.", zeroResult.getErrorMessage());

        MutationResult negResult = balanceService.executeDebit(1L, new BigDecimal("-50.0000"), "REF-004");
        assertFalse(negResult.isSuccess());
        assertEquals("Debit amount must be greater than zero.", negResult.getErrorMessage());

        verify(balanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("FSE-304 & FSE-305: PostgreSQL audit store failure throws DualWriteAuditException to trigger rollback")
    void testExecuteDebit_AuditFailureTriggersRollback() {
        when(balanceRepository.findByAccountId(1L)).thenReturn(Optional.of(mockBalance));

        Transaction savedTxn = Transaction.builder()
                .transactionId(1002L)
                .account(mockAccount)
                .referenceNo("REF-FAIL-01")
                .transactionType("DEBIT")
                .amount(new BigDecimal("150.0000"))
                .previousBalance(new BigDecimal("1000.0000"))
                .newBalance(new BigDecimal("850.0000"))
                .build();

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTxn);

        // Simulate PostgreSQL database network disconnect / trigger failure
        doThrow(new DualWriteAuditException("PostgreSQL audit store write failed: Connection refused"))
                .when(dualWriteAuditService)
                .recordMutationAudit(any(), any(), any(), any(), any(), any(), any(), any());

        assertThrows(DualWriteAuditException.class, () ->
                balanceService.executeDebit(1L, new BigDecimal("150.0000"), "REF-FAIL-01")
        );

        // Verify that dual write was attempted
        verify(dualWriteAuditService, times(1)).recordMutationAudit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("FSE-303 & FSE-304: Successful credit with simultaneous Oracle and PostgreSQL dual-write")
    void testExecuteCredit_Success() {
        when(balanceRepository.findByAccountId(1L)).thenReturn(Optional.of(mockBalance));

        Transaction savedTxn = Transaction.builder()
                .transactionId(2001L)
                .account(mockAccount)
                .referenceNo("REF-CREDIT-01")
                .transactionType("CREDIT")
                .amount(new BigDecimal("500.0000"))
                .previousBalance(new BigDecimal("1000.0000"))
                .newBalance(new BigDecimal("1500.0000"))
                .status("COMPLETED")
                .build();

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTxn);

        MutationResult result = balanceService.executeCredit(1L, new BigDecimal("500.0000"), "REF-CREDIT-01");

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("1000.0000"), result.getPreviousBalance());
        assertEquals(new BigDecimal("1500.0000"), result.getNewBalance());

        verify(balanceRepository, times(1)).save(mockBalance);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(dualWriteAuditService, times(1)).recordMutationAudit(
                eq(2001L),
                eq(1L),
                eq("CREDIT"),
                eq(new BigDecimal("500.0000")),
                eq(new BigDecimal("1000.0000")),
                eq(new BigDecimal("1500.0000")),
                any(),
                any()
        );
    }
}