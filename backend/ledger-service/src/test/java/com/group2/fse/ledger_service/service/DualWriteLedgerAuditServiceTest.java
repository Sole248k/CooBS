package com.group2.fse.ledger_service.service;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.group2.fse.ledger_service.exception.DualWriteAuditException;

@DisplayName("DualWriteLedgerAuditService Unit Tests (FSE-304 & FSE-305)")
class DualWriteLedgerAuditServiceTest {

    @Mock
    private JdbcTemplate auditJdbcTemplate;

    @InjectMocks
    private DualWriteLedgerAuditService auditService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("FSE-304: Successfully executes SQL insert into PostgreSQL audit store")
    void testRecordMutationAudit_Success() {
        when(auditJdbcTemplate.update(
                eq(DualWriteLedgerAuditService.INSERT_AUDIT_SQL),
                eq(101L),
                eq(1L),
                eq("DEBIT"),
                eq(new BigDecimal("2500.0000")),
                eq(new BigDecimal("10000.0000")),
                eq(new BigDecimal("7500.0000")),
                eq(99L),
                eq("192.168.1.100")
        )).thenReturn(1);

        assertDoesNotThrow(() ->
                auditService.recordMutationAudit(
                        101L,
                        1L,
                        "DEBIT",
                        new BigDecimal("2500.0000"),
                        new BigDecimal("10000.0000"),
                        new BigDecimal("7500.0000"),
                        99L,
                        "192.168.1.100"
                )
        );

        verify(auditJdbcTemplate, times(1)).update(
                eq(DualWriteLedgerAuditService.INSERT_AUDIT_SQL),
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("FSE-305: Connection failure throws DualWriteAuditException to trigger rollback")
    void testRecordMutationAudit_ConnectionFailure_ThrowsException() {
        when(auditJdbcTemplate.update(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection to PostgreSQL"));

        DualWriteAuditException ex = assertThrows(DualWriteAuditException.class, () ->
                auditService.recordMutationAudit(
                        102L,
                        1L,
                        "DEBIT",
                        new BigDecimal("500.0000"),
                        new BigDecimal("2000.0000"),
                        new BigDecimal("1500.0000"),
                        null,
                        null
                )
        );

        assertTrue(ex.getMessage().contains("PostgreSQL audit store write failed"));
        assertTrue(ex.getCause() instanceof CannotGetJdbcConnectionException);
    }

    @Test
    @DisplayName("FSE-305: Constraint violation or trigger failure throws DualWriteAuditException")
    void testRecordMutationAudit_ConstraintViolation_ThrowsException() {
        when(auditJdbcTemplate.update(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("COMPLIANCE VIOLATION: Hash mismatch"));

        DualWriteAuditException ex = assertThrows(DualWriteAuditException.class, () ->
                auditService.recordMutationAudit(
                        103L,
                        2L,
                        "CREDIT",
                        new BigDecimal("1000.0000"),
                        new BigDecimal("500.0000"),
                        new BigDecimal("1500.0000"),
                        1L,
                        "127.0.0.1"
                )
        );

        assertTrue(ex.getMessage().contains("COMPLIANCE VIOLATION"));
    }

    @Test
    @DisplayName("FSE-305: Zero rows affected throws DualWriteAuditException")
    void testRecordMutationAudit_ZeroRowsAffected_ThrowsException() {
        when(auditJdbcTemplate.update(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        DualWriteAuditException ex = assertThrows(DualWriteAuditException.class, () ->
                auditService.recordMutationAudit(
                        104L,
                        3L,
                        "DEBIT",
                        new BigDecimal("100.0000"),
                        new BigDecimal("300.0000"),
                        new BigDecimal("200.0000"),
                        null,
                        "127.0.0.1"
                )
        );

        assertEquals("Audit record insertion affected 0 rows, expected 1.", ex.getMessage());
    }

    private void record(long transactionId, long accountId, String type) {
        auditService.recordMutationAudit(transactionId, accountId, type, new BigDecimal("100.0000"),
                new BigDecimal("300.0000"), new BigDecimal("200.0000"), 3L, "127.0.0.1");
    }

    /** Runs the body as if inside a ledger transaction, and hands it the one callback the service registered. */
    private void insideALedgerTransaction(java.util.function.Consumer<java.util.function.Supplier<TransactionSynchronization>> body) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.accept(() -> {
                assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
                return TransactionSynchronizationManager.getSynchronizations().get(0);
            });
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(auditService);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Inside a ledger transaction nothing is written until it is about to commit; then every row, in order")
    void rowsAreWrittenOnlyWhenTheLedgerTransactionCommits() {
        when(auditJdbcTemplate.update(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        insideALedgerTransaction(registered -> {
            record(201L, 1L, "DEBIT");
            record(202L, 2L, "CREDIT");
            verify(auditJdbcTemplate, never()).update(any(), any(), any(), any(), any(), any(), any(), any(), any());

            registered.get().beforeCommit(false);

            InOrder written = inOrder(auditJdbcTemplate);
            written.verify(auditJdbcTemplate).update(any(), eq(201L), eq(1L), eq("DEBIT"), any(), any(), any(), any(), any());
            written.verify(auditJdbcTemplate).update(any(), eq(202L), eq(2L), eq("CREDIT"), any(), any(), any(), any(), any());

            registered.get().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            assertNull(TransactionSynchronizationManager.getResource(auditService));
        });
    }

    @Test
    @DisplayName("A posting that rolls back leaves no audit row: the table is append-only and could never lose it again")
    void rolledBackPostingWritesNothing() {
        insideALedgerTransaction(registered -> {
            record(301L, 1L, "DEBIT");
            record(302L, 2L, "CREDIT");

            // A rollback never reaches beforeCommit.
            registered.get().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(auditJdbcTemplate, never()).update(any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertNull(TransactionSynchronizationManager.getResource(auditService));
        });
    }

    @Test
    @DisplayName("FSE-305 still holds at commit: an audit store that refuses the rows fails the commit, so Oracle rolls back")
    void auditFailureAtCommitStopsTheCommit() {
        when(auditJdbcTemplate.update(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new CannotGetJdbcConnectionException("audit store is down"));

        insideALedgerTransaction(registered -> {
            record(401L, 1L, "DEBIT");

            assertThrows(DualWriteAuditException.class, () -> registered.get().beforeCommit(false));
        });
    }
}
