package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.entity.User;

import java.math.BigDecimal;

/**
 * Core Posting Engine interface extracting row-level pessimistic locking,
 * deadlock-free multi-account lock ordering, balance arithmetic, transaction
 * persistence, and forensic audit recording.
 *
 * Designed so that Standard Mutations, Holds, and Reversals can share
 * identical lock and balance math without editing AccountBalanceServiceImpl.
 */
public interface PostingEngine {

    /**
     * Acquires a row-level pessimistic write lock on an account balance record.
     * Enforces active account status check.
     *
     * @param accountId account identifier
     * @return locked Balance entity
     */
    Balance lockAccount(Long accountId);

    /**
     * Acquires a row-level pessimistic write lock with optional active status validation.
     *
     * @param accountId account identifier
     * @param enforceActiveStatus whether to verify account status == ACTIVE
     * @return locked Balance entity
     */
    Balance lockAccount(Long accountId, boolean enforceActiveStatus);

    /**
     * Deadlock-free ordered pessimistic row locking for multi-account operations (e.g. transfers, reversals).
     * Locks min(src, dst) then max(src, dst) to eliminate circular lock dependencies.
     *
     * @param sourceAccountId source account ID
     * @param destinationAccountId destination account ID
     * @return OrderedAccountLocks containing both locked Balance entities
     */
    OrderedAccountLocks lockAccountsInOrder(Long sourceAccountId, Long destinationAccountId);

    /**
     * Validates that the amount is non-null and strictly greater than zero.
     *
     * @param amount amount to validate
     * @param operation operation name for error messaging
     */
    void validatePositiveAmount(BigDecimal amount, String operation);

    /**
     * Validates that the account possesses sufficient available balance (zero-floor invariant).
     *
     * @param accountId account ID
     * @param availableBalance current available balance
     * @param amount debit/hold amount required
     */
    void validateSufficientFunds(Long accountId, BigDecimal availableBalance, BigDecimal amount);

    /**
     * Calculates the new balance after debit: current - amount (scale = 4).
     */
    BigDecimal calculateDebit(BigDecimal currentBalance, BigDecimal amount);

    /**
     * Calculates the new balance after credit: current + amount (scale = 4).
     */
    BigDecimal calculateCredit(BigDecimal currentBalance, BigDecimal amount);

    /**
     * Applies debit math to a locked balance, verifies funds sufficiency, and persists the balance.
     */
    Balance applyDebit(Balance balance, BigDecimal amount);

    /**
     * Applies credit math to a locked balance and persists the balance.
     */
    Balance applyCredit(Balance balance, BigDecimal amount);

    /**
     * Records and persists an Oracle Master SoR Transaction entity.
     */
    Transaction recordTransaction(
            Account account,
            String referenceNo,
            String transactionType,
            BigDecimal amount,
            BigDecimal previousBalance,
            BigDecimal newBalance,
            String status,
            User approvedBy);

    /**
     * Synchronously records audit trail in PostgreSQL Forensic Audit Store.
     */
    void recordAudit(
            Long transactionId,
            Long accountId,
            String transactionType,
            BigDecimal amount,
            BigDecimal previousBalance,
            BigDecimal newBalance,
            Long actorId,
            String clientIp);

    /**
     * High-level atomic single-account debit posting (Lock -> Validate -> Balance Math -> Txn -> Audit).
     */
    PostingLegResult postDebit(
            Long accountId,
            BigDecimal amount,
            String referenceNo,
            Long actorId,
            String clientIp,
            boolean enforceActiveStatus);

    /**
     * High-level atomic single-account credit posting (Lock -> Validate -> Balance Math -> Txn -> Audit).
     */
    PostingLegResult postCredit(
            Long accountId,
            BigDecimal amount,
            String referenceNo,
            Long actorId,
            String clientIp,
            boolean enforceActiveStatus);

    /**
     * High-level atomic two-account double-entry transfer posting.
     */
    PostingTransferResult postTransfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            String referenceNo,
            Long actorId,
            String clientIp);
}
