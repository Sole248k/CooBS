package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.entity.User;
import com.group2.fse.ledger_service.exception.AccountNotActiveException;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.service.DualWriteLedgerAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Production implementation of PostingEngine coordinating row-level pessimistic locks,
 * deadlock-free multi-account ordering, zero-overdraft balance arithmetic, SoR transaction
 * ledger entries, and PostgreSQL forensic audit dual-writes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingEngineImpl implements PostingEngine {

    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final DualWriteLedgerAuditService dualWriteLedgerAuditService;

    @Override
    public Balance lockAccount(Long accountId) {
        return lockAccount(accountId, true);
    }

    @Override
    public Balance lockAccount(Long accountId, boolean enforceActiveStatus) {
        if (accountId == null) {
            throw new InvalidTransactionException("Account ID must not be null.");
        }

        Balance balance = balanceRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (enforceActiveStatus && balance.getAccount() != null && balance.getAccount().getStatus() != null &&
                !"ACTIVE".equalsIgnoreCase(balance.getAccount().getStatus())) {
            throw new AccountNotActiveException(accountId, balance.getAccount().getStatus());
        }

        return balance;
    }

    @Override
    public OrderedAccountLocks lockAccountsInOrder(Long sourceAccountId, Long destinationAccountId) {
        if (sourceAccountId == null || destinationAccountId == null) {
            throw new InvalidTransactionException("Source and destination account IDs are required.");
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new InvalidTransactionException("Source and destination accounts must be distinct accounts.");
        }

        // Deadlock-free ordered lock acquisition: min(src, dst) then max(src, dst)
        Long firstId = Math.min(sourceAccountId, destinationAccountId);
        Long secondId = Math.max(sourceAccountId, destinationAccountId);

        Balance firstBalance = balanceRepository.findByAccountId(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));

        Balance secondBalance = balanceRepository.findByAccountId(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        Balance sourceBalance = sourceAccountId.equals(firstId) ? firstBalance : secondBalance;
        Balance destinationBalance = destinationAccountId.equals(firstId) ? firstBalance : secondBalance;

        // Verify active statuses
        if (sourceBalance.getAccount() != null && sourceBalance.getAccount().getStatus() != null &&
                !"ACTIVE".equalsIgnoreCase(sourceBalance.getAccount().getStatus())) {
            throw new AccountNotActiveException(sourceAccountId, sourceBalance.getAccount().getStatus());
        }

        if (destinationBalance.getAccount() != null && destinationBalance.getAccount().getStatus() != null &&
                !"ACTIVE".equalsIgnoreCase(destinationBalance.getAccount().getStatus())) {
            throw new AccountNotActiveException(destinationAccountId, destinationBalance.getAccount().getStatus());
        }

        return new OrderedAccountLocks(sourceBalance, destinationBalance);
    }

    @Override
    public void validatePositiveAmount(BigDecimal amount, String operation) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException(operation + " amount must be greater than zero.");
        }
    }

    @Override
    public void validateSufficientFunds(Long accountId, BigDecimal availableBalance, BigDecimal amount) {
        if (availableBalance == null || availableBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId, availableBalance != null ? availableBalance : BigDecimal.ZERO, amount);
        }
    }

    @Override
    public BigDecimal calculateDebit(BigDecimal currentBalance, BigDecimal amount) {
        return currentBalance.subtract(amount).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateCredit(BigDecimal currentBalance, BigDecimal amount) {
        return currentBalance.add(amount).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Balance post(Balance balance, BigDecimal availableDelta, BigDecimal ledgerDelta) {
        if (balance == null || availableDelta == null || ledgerDelta == null) {
            throw new InvalidTransactionException("Balance, available delta and ledger delta are required for posting.");
        }

        balance.setAvailableBalance(calculateCredit(balance.getAvailableBalance(), availableDelta));
        balance.setLedgerBalance(calculateCredit(balance.getLedgerBalance(), ledgerDelta));
        balanceRepository.save(balance);
        return balance;
    }

    @Override
    public Balance applyDebit(Balance balance, BigDecimal amount) {
        validateSufficientFunds(balance.getAccountId(), balance.getAvailableBalance(), amount);
        return post(balance, amount.negate(), amount.negate());
    }

    @Override
    public Balance applyCredit(Balance balance, BigDecimal amount) {
        return post(balance, amount, amount);
    }

    @Override
    public Transaction recordTransaction(
            Account account,
            String referenceNo,
            String transactionType,
            BigDecimal amount,
            BigDecimal previousBalance,
            BigDecimal newBalance,
            String status,
            User approvedBy) {

        Transaction txn = Transaction.builder()
                .account(account)
                .referenceNo(referenceNo)
                .transactionType(transactionType)
                .amount(amount != null ? amount.setScale(4, RoundingMode.HALF_UP) : null)
                .previousBalance(previousBalance != null ? previousBalance.setScale(4, RoundingMode.HALF_UP) : null)
                .newBalance(newBalance != null ? newBalance.setScale(4, RoundingMode.HALF_UP) : null)
                .status(status != null ? status : "COMPLETED")
                .approvedBy(approvedBy)
                .build();

        return transactionRepository.save(txn);
    }

    @Override
    public void recordAudit(
            Long transactionId,
            Long accountId,
            String transactionType,
            BigDecimal amount,
            BigDecimal previousBalance,
            BigDecimal newBalance,
            Long actorId,
            String clientIp) {

        dualWriteLedgerAuditService.recordMutationAudit(
                transactionId != null ? transactionId : 0L,
                accountId,
                transactionType,
                amount,
                previousBalance,
                newBalance,
                actorId,
                clientIp
        );
    }

    @Override
    public PostingLegResult postDebit(
            Long accountId,
            BigDecimal amount,
            String referenceNo,
            Long actorId,
            String clientIp,
            boolean enforceActiveStatus) {

        validatePositiveAmount(amount, "Debit");
        Balance balance = lockAccount(accountId, enforceActiveStatus);

        BigDecimal currentBalance = balance.getAvailableBalance();
        BigDecimal newBalance = applyDebit(balance, amount).getAvailableBalance();

        Account account = balance.getAccount() != null ? balance.getAccount() : Account.builder().accountId(accountId).build();
        User approvedBy = actorId != null ? User.builder().userId(actorId).build() : null;

        Transaction txn = recordTransaction(
                account,
                referenceNo,
                "DEBIT",
                amount,
                currentBalance,
                newBalance,
                "COMPLETED",
                approvedBy
        );

        Long txnId = txn != null && txn.getTransactionId() != null ? txn.getTransactionId() : 0L;
        recordAudit(txnId, accountId, "DEBIT", amount, currentBalance, newBalance, actorId, clientIp);

        String accNum = account.getAccountNumber();
        Long custId = account.getCustomer() != null ? account.getCustomer().getCustomerId() : null;

        return PostingLegResult.builder()
                .accountId(accountId)
                .accountNumber(accNum)
                .customerId(custId)
                .transactionId(txnId)
                .referenceNo(referenceNo)
                .transactionType("DEBIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .transaction(txn)
                .balance(balance)
                .build();
    }

    @Override
    public PostingLegResult postCredit(
            Long accountId,
            BigDecimal amount,
            String referenceNo,
            Long actorId,
            String clientIp,
            boolean enforceActiveStatus) {

        validatePositiveAmount(amount, "Credit");
        Balance balance = lockAccount(accountId, enforceActiveStatus);

        BigDecimal currentBalance = balance.getAvailableBalance();
        BigDecimal newBalance = applyCredit(balance, amount).getAvailableBalance();

        Account account = balance.getAccount() != null ? balance.getAccount() : Account.builder().accountId(accountId).build();
        User approvedBy = actorId != null ? User.builder().userId(actorId).build() : null;

        Transaction txn = recordTransaction(
                account,
                referenceNo,
                "CREDIT",
                amount,
                currentBalance,
                newBalance,
                "COMPLETED",
                approvedBy
        );

        Long txnId = txn != null && txn.getTransactionId() != null ? txn.getTransactionId() : 0L;
        recordAudit(txnId, accountId, "CREDIT", amount, currentBalance, newBalance, actorId, clientIp);

        String accNum = account.getAccountNumber();
        Long custId = account.getCustomer() != null ? account.getCustomer().getCustomerId() : null;

        return PostingLegResult.builder()
                .accountId(accountId)
                .accountNumber(accNum)
                .customerId(custId)
                .transactionId(txnId)
                .referenceNo(referenceNo)
                .transactionType("CREDIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .transaction(txn)
                .balance(balance)
                .build();
    }

    @Override
    public PostingTransferResult postTransfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            String referenceNo,
            Long actorId,
            String clientIp) {

        validatePositiveAmount(amount, "Transfer");
        OrderedAccountLocks locks = lockAccountsInOrder(sourceAccountId, destinationAccountId);

        Balance sourceBalance = locks.getSourceBalance();
        Balance destinationBalance = locks.getDestinationBalance();

        BigDecimal srcPrev = sourceBalance.getAvailableBalance();
        BigDecimal dstPrev = destinationBalance.getAvailableBalance();

        BigDecimal srcNew = applyDebit(sourceBalance, amount).getAvailableBalance();
        BigDecimal dstNew = applyCredit(destinationBalance, amount).getAvailableBalance();

        String refDebit = referenceNo != null && referenceNo.length() > 33
                ? referenceNo.substring(0, 33) + "-D"
                : (referenceNo != null ? referenceNo + "-D" : null);
        String refCredit = referenceNo != null && referenceNo.length() > 33
                ? referenceNo.substring(0, 33) + "-C"
                : (referenceNo != null ? referenceNo + "-C" : null);

        Account srcAccount = sourceBalance.getAccount() != null
                ? sourceBalance.getAccount()
                : Account.builder().accountId(sourceAccountId).build();
        Account dstAccount = destinationBalance.getAccount() != null
                ? destinationBalance.getAccount()
                : Account.builder().accountId(destinationAccountId).build();
        User approvedByUser = actorId != null ? User.builder().userId(actorId).build() : null;

        Transaction debitTxn = recordTransaction(srcAccount, refDebit, "DEBIT", amount, srcPrev, srcNew, "COMPLETED", approvedByUser);
        Transaction creditTxn = recordTransaction(dstAccount, refCredit, "CREDIT", amount, dstPrev, dstNew, "COMPLETED", approvedByUser);

        Long debitTxnId = debitTxn != null && debitTxn.getTransactionId() != null ? debitTxn.getTransactionId() : 0L;
        Long creditTxnId = creditTxn != null && creditTxn.getTransactionId() != null ? creditTxn.getTransactionId() : 0L;

        recordAudit(debitTxnId, sourceAccountId, "DEBIT", amount, srcPrev, srcNew, actorId, clientIp);
        recordAudit(creditTxnId, destinationAccountId, "CREDIT", amount, dstPrev, dstNew, actorId, clientIp);

        String srcAccNum = srcAccount.getAccountNumber();
        String dstAccNum = dstAccount.getAccountNumber();
        Long srcCustId = srcAccount.getCustomer() != null ? srcAccount.getCustomer().getCustomerId() : null;
        Long dstCustId = dstAccount.getCustomer() != null ? dstAccount.getCustomer().getCustomerId() : null;

        return PostingTransferResult.builder()
                .transferReference(referenceNo)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .sourceAccountNumber(srcAccNum)
                .destinationAccountNumber(dstAccNum)
                .sourceCustomerId(srcCustId)
                .destinationCustomerId(dstCustId)
                .amount(amount)
                .sourcePreviousBalance(srcPrev)
                .sourceNewBalance(srcNew)
                .destinationPreviousBalance(dstPrev)
                .destinationNewBalance(dstNew)
                .debitTransaction(debitTxn)
                .creditTransaction(creditTxn)
                .build();
    }
}
