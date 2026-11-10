package com.group2.fse.ledger_service.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.group2.fse.ledger_service.exception.DualWriteAuditException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Service responsible for executing simultaneous dual-write updates to the
 * immutable PostgreSQL forensic audit store (FSE-304).
 *
 * PostgreSQL triggers automatically calculate the SHA-256 tamper-evident hash chain.
 * Any write failure triggers a DualWriteAuditException, rolling back the Oracle
 * master ledger transaction (FSE-305).
 *
 * The audit table is append-only, so a row written for a posting that then rolls back can
 * never be taken out again. For that reason the rows of a posting are not written when they
 * are recorded. They are kept until the Oracle transaction is about to commit: everything the
 * posting changed is first sent to Oracle, so that any rule it breaks shows now, and only then
 * are its audit rows written, together. A posting that fails for any reason before that point
 * leaves no audit row.
 *
 * One gap is left: the audit rows are in, and then the Oracle COMMIT itself fails (the
 * connection drops at that instant). Closing it would mean writing the audit rows after the
 * commit, from an outbox, and a posting would then succeed while the audit store is down.
 */
@Service
public class DualWriteLedgerAuditService {

    private static final Logger log = LoggerFactory.getLogger(DualWriteLedgerAuditService.class);

    public static final String INSERT_AUDIT_SQL = """
        INSERT INTO audit_store.ledger_mutation_audit
        (transaction_id, account_id, transaction_type, amount, old_balance, new_balance, actor_id, client_ip)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private final JdbcTemplate auditJdbcTemplate;
    /** One audit-store transaction for all the rows of a posting: both legs are written, or neither. */
    private final TransactionOperations auditTransaction;

    @PersistenceContext
    private EntityManager entityManager;

    public DualWriteLedgerAuditService(@Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbcTemplate) {
        this.auditJdbcTemplate = auditJdbcTemplate;
        DataSource auditStore = auditJdbcTemplate.getDataSource();
        // Built here and not as a bean: a second transaction manager in the context would make
        // every @Transactional in this service ambiguous. They all mean the Oracle one.
        this.auditTransaction = auditStore == null
                ? TransactionOperations.withoutTransaction()
                : new TransactionTemplate(new DataSourceTransactionManager(auditStore));
    }

    /**
     * Records one audit row for the PostgreSQL immutable audit store.
     * The PostgreSQL BEFORE INSERT trigger (trg_compute_hash_chain) automatically
     * computes the SHA-256 tamper-evident hash chain across the ledger records.
     *
     * Inside a ledger transaction the row is written just before that transaction commits
     * (see the class comment). Outside one it is written at once.
     *
     * If the write fails, a DualWriteAuditException is thrown to trigger
     * automated transaction rollback in the Oracle primary datasource (FSE-305).
     */
    public void recordMutationAudit(
            Long transactionId,
            Long accountId,
            String transactionType,
            BigDecimal amount,
            BigDecimal oldBalance,
            BigDecimal newBalance,
            Long actorId,
            String clientIp) {

        Object[] row = {transactionId, accountId, transactionType, amount, oldBalance, newBalance, actorId,
                clientIp != null ? clientIp : "127.0.0.1"};

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write(row);
            return;
        }
        rowsOfThisTransaction().add(row);
    }

    /** The rows recorded so far in the running ledger transaction; registers their write on first use. */
    @SuppressWarnings("unchecked")
    private List<Object[]> rowsOfThisTransaction() {
        List<Object[]> rows = (List<Object[]>) TransactionSynchronizationManager.getResource(this);
        if (rows != null) {
            return rows;
        }
        List<Object[]> pending = new ArrayList<>();
        TransactionSynchronizationManager.bindResource(this, pending);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (entityManager != null) {
                    // Balance updates wait in the persistence context until commit. Sent now, a
                    // broken rule (a negative balance, a stale row) fails here, before any audit row exists.
                    entityManager.flush();
                }
                auditTransaction.executeWithoutResult(status -> pending.forEach(DualWriteLedgerAuditService.this::write));
            }

            @Override
            public void afterCompletion(int status) {
                // Committed or rolled back, the rows are done with. After a rollback they were never written.
                TransactionSynchronizationManager.unbindResourceIfPossible(DualWriteLedgerAuditService.this);
            }
        });
        return pending;
    }

    private void write(Object[] row) {
        Object transactionId = row[0];
        log.info("Initiating dual-write audit record: txnId={}, accountId={}, type={}, amount={}",
                transactionId, row[1], row[2], row[3]);

        try {
            int rowsAffected = auditJdbcTemplate.update(INSERT_AUDIT_SQL, row);

            if (rowsAffected != 1) {
                throw new DualWriteAuditException("Audit record insertion affected " + rowsAffected + " rows, expected 1.");
            }

            log.info("Successfully persisted immutable audit record in PostgreSQL for txnId={}", transactionId);

        } catch (DataAccessException ex) {
            log.error("CRITICAL: Failed to write to PostgreSQL audit store for txnId={}. Error: {}",
                    transactionId, ex.getMessage(), ex);
            throw new DualWriteAuditException(
                    "PostgreSQL audit store write failed for transaction " + transactionId + ": " + ex.getMessage(),
                    ex
            );
        }
    }
}
