package com.group2.fse.ledger_service.service.impl;

import com.group2.fse.ledger_service.dto.BalanceResponseDto;
import com.group2.fse.ledger_service.dto.DebitCreditRequestDto;
import com.group2.fse.ledger_service.dto.DebitCreditResponseDto;
import com.group2.fse.ledger_service.dto.MutationResult;
import com.group2.fse.ledger_service.dto.TransferRequestDto;
import com.group2.fse.ledger_service.dto.TransferResponseDto;
import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.entity.User;
import com.group2.fse.ledger_service.event.LedgerMutationEvent;
import com.group2.fse.ledger_service.event.LedgerTransferEvent;
import com.group2.fse.ledger_service.event.LedgerTransferFailedEvent;
import com.group2.fse.ledger_service.exception.AccountNotActiveException;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.service.AccountBalanceService;
import com.group2.fse.ledger_service.service.DualWriteLedgerAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Core Balance Mutation Engine coordinating Oracle Master SoR and PostgreSQL Forensic Audit Store.
 * Implements:
 * - FSE-302: Row-level pessimistic locking via BalanceRepository.findByAccountId with timeout hints
 * - FSE-303: Zero-balance floor and atomic debit/credit operations
 * - FSE-304: Simultaneous dual-write updates across Oracle and PostgreSQL
 * - FSE-305: Automated rollback via @Transactional(rollbackFor = Exception.class) on audit failure
 * - FSE-306: Deadlock-free ordered pessimistic row locking for multi-account atomic transfers
 * - FSE-501: Transactional domain event publishing via ApplicationEventPublisher on AFTER_COMMIT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBalanceServiceImpl implements AccountBalanceService {

    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final DualWriteLedgerAuditService dualWriteLedgerAuditService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Autowired(required = false)
    private com.group2.fse.ledger_service.service.OutboxService outboxService;

    // =========================================================================
    // Legacy methods preserved for backwards compatibility with existing tests
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutationResult executeDebit(Long accountId, BigDecimal amount, String referenceNo) {
        return executeDebit(accountId, amount, referenceNo, null, "127.0.0.1");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutationResult executeDebit(Long accountId, BigDecimal amount, String referenceNo, Long actorId, String clientIp) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return MutationResult.failure(accountId, referenceNo, "Debit amount must be greater than zero.");
        }

        Optional<Balance> balanceOpt = balanceRepository.findByAccountId(accountId);
        if (balanceOpt.isEmpty()) {
            balanceOpt = balanceRepository.findById(accountId);
        }
        if (balanceOpt.isEmpty()) {
            return MutationResult.failure(accountId, referenceNo, "Account balance record not found.");
        }

        Balance balance = balanceOpt.get();
        BigDecimal currentBalance = balance.getAvailableBalance();

        if (currentBalance.compareTo(amount) < 0) {
            return MutationResult.failure(accountId, referenceNo, "Insufficient funds for debit operation.");
        }

        BigDecimal newBalance = currentBalance.subtract(amount);
        balance.setAvailableBalance(newBalance);
        balanceRepository.save(balance);

        Account account = balance.getAccount();
        if (account == null) {
            account = Account.builder().accountId(accountId).build();
        }

        Transaction txn = Transaction.builder()
                .account(account)
                .referenceNo(referenceNo)
                .transactionType("DEBIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .approvedBy(actorId != null ? User.builder().userId(actorId).build() : null)
                .build();

        Transaction savedTxn = transactionRepository.save(txn);
        Long txnId = savedTxn != null ? savedTxn.getTransactionId() : null;

        dualWriteLedgerAuditService.recordMutationAudit(
                txnId != null ? txnId : 0L,
                accountId,
                "DEBIT",
                amount,
                currentBalance,
                newBalance,
                actorId,
                clientIp
        );

        publishMutationEvent(
                txnId != null ? txnId : 0L,
                accountId,
                account != null ? account.getAccountNumber() : null,
                "DEBIT",
                amount,
                currentBalance,
                newBalance,
                referenceNo,
                account != null && account.getCustomer() != null ? account.getCustomer().getCustomerId() : null
        );

        return MutationResult.success(accountId, referenceNo, txnId, currentBalance, newBalance, amount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutationResult executeCredit(Long accountId, BigDecimal amount, String referenceNo) {
        return executeCredit(accountId, amount, referenceNo, null, "127.0.0.1");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutationResult executeCredit(Long accountId, BigDecimal amount, String referenceNo, Long actorId, String clientIp) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return MutationResult.failure(accountId, referenceNo, "Credit amount must be greater than zero.");
        }

        Optional<Balance> balanceOpt = balanceRepository.findByAccountId(accountId);
        if (balanceOpt.isEmpty()) {
            balanceOpt = balanceRepository.findById(accountId);
        }
        if (balanceOpt.isEmpty()) {
            return MutationResult.failure(accountId, referenceNo, "Account balance record not found.");
        }

        Balance balance = balanceOpt.get();
        BigDecimal currentBalance = balance.getAvailableBalance();
        BigDecimal newBalance = currentBalance.add(amount);

        balance.setAvailableBalance(newBalance);
        balanceRepository.save(balance);

        Account account = balance.getAccount();
        if (account == null) {
            account = Account.builder().accountId(accountId).build();
        }

        Transaction txn = Transaction.builder()
                .account(account)
                .referenceNo(referenceNo)
                .transactionType("CREDIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .approvedBy(actorId != null ? User.builder().userId(actorId).build() : null)
                .build();

        Transaction savedTxn = transactionRepository.save(txn);
        Long txnId = savedTxn != null ? savedTxn.getTransactionId() : null;

        dualWriteLedgerAuditService.recordMutationAudit(
                txnId != null ? txnId : 0L,
                accountId,
                "CREDIT",
                amount,
                currentBalance,
                newBalance,
                actorId,
                clientIp
        );

        publishMutationEvent(
                txnId != null ? txnId : 0L,
                accountId,
                account != null ? account.getAccountNumber() : null,
                "CREDIT",
                amount,
                currentBalance,
                newBalance,
                referenceNo,
                account != null && account.getCustomer() != null ? account.getCustomer().getCustomerId() : null
        );

        return MutationResult.success(accountId, referenceNo, txnId, currentBalance, newBalance, amount);
    }

    // =========================================================================
    // Enterprise REST API Service Implementations (Exceptions on domain errors)
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferResponseDto executeTransfer(TransferRequestDto request) {
        return executeTransfer(request, null, "127.0.0.1");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferResponseDto executeTransfer(TransferRequestDto request, Long actorId, String clientIp) {
        Long srcId = request.getSourceAccountId();
        Long dstId = request.getDestinationAccountId();
        BigDecimal amount = request.getAmount();
        String ref = request.getReferenceNo();
        Long srcCustId = null;

        try {
            if (srcId == null || dstId == null) {
                throw new InvalidTransactionException("Source and destination account IDs are required.");
            }
            if (srcId.equals(dstId)) {
                throw new InvalidTransactionException("Source and destination accounts must be distinct accounts.");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidTransactionException("Transfer amount must be greater than zero.");
            }

            // Deadlock-free ordered lock acquisition: min(src, dst) then max(src, dst)
            Long firstId = Math.min(srcId, dstId);
            Long secondId = Math.max(srcId, dstId);

            Balance firstBalance = balanceRepository.findByAccountId(firstId)
                    .or(() -> balanceRepository.findById(firstId))
                    .orElseThrow(() -> new AccountNotFoundException(firstId));

            Balance secondBalance = balanceRepository.findByAccountId(secondId)
                    .or(() -> balanceRepository.findById(secondId))
                    .orElseThrow(() -> new AccountNotFoundException(secondId));

            Balance sourceBalance = srcId.equals(firstId) ? firstBalance : secondBalance;
            Balance destinationBalance = dstId.equals(firstId) ? firstBalance : secondBalance;

            // Status invariants: Reject transfers if either source or destination account is not ACTIVE (e.g. FROZEN, CLOSED)
            if (sourceBalance.getAccount() != null && sourceBalance.getAccount().getStatus() != null &&
                    !"ACTIVE".equalsIgnoreCase(sourceBalance.getAccount().getStatus())) {
                throw new AccountNotActiveException(srcId, sourceBalance.getAccount().getStatus());
            }

            if (destinationBalance.getAccount() != null && destinationBalance.getAccount().getStatus() != null &&
                    !"ACTIVE".equalsIgnoreCase(destinationBalance.getAccount().getStatus())) {
                throw new AccountNotActiveException(dstId, destinationBalance.getAccount().getStatus());
            }

            if (sourceBalance.getAccount() != null && sourceBalance.getAccount().getCustomer() != null) {
                srcCustId = sourceBalance.getAccount().getCustomer().getCustomerId();
            }

            BigDecimal srcPrev = sourceBalance.getAvailableBalance();
            if (srcPrev.compareTo(amount) < 0) {
                throw new InsufficientFundsException(srcId, srcPrev, amount);
            }

            BigDecimal srcNew = srcPrev.subtract(amount);
            BigDecimal dstPrev = destinationBalance.getAvailableBalance();
            BigDecimal dstNew = dstPrev.add(amount);

            sourceBalance.setAvailableBalance(srcNew);
            destinationBalance.setAvailableBalance(dstNew);
            balanceRepository.save(sourceBalance);
            balanceRepository.save(destinationBalance);

            String refDebit = ref != null && ref.length() > 33 ? ref.substring(0, 33) + "-D" : (ref != null ? ref + "-D" : null);
            String refCredit = ref != null && ref.length() > 33 ? ref.substring(0, 33) + "-C" : (ref != null ? ref + "-C" : null);

            // Oracle Master SoR: Save paired transaction records
            Account srcAccount = sourceBalance.getAccount() != null ? sourceBalance.getAccount() : Account.builder().accountId(srcId).build();
            Account dstAccount = destinationBalance.getAccount() != null ? destinationBalance.getAccount() : Account.builder().accountId(dstId).build();
            User approvedByUser = actorId != null ? User.builder().userId(actorId).build() : null;

            Transaction debitTxn = Transaction.builder()
                    .account(srcAccount)
                    .referenceNo(refDebit)
                    .transactionType("DEBIT")
                    .amount(amount)
                    .previousBalance(srcPrev)
                    .newBalance(srcNew)
                    .status("COMPLETED")
                    .approvedBy(approvedByUser)
                    .build();
            Transaction savedDebitTxn = transactionRepository.save(debitTxn);

            Transaction creditTxn = Transaction.builder()
                    .account(dstAccount)
                    .referenceNo(refCredit)
                    .transactionType("CREDIT")
                    .amount(amount)
                    .previousBalance(dstPrev)
                    .newBalance(dstNew)
                    .status("COMPLETED")
                    .approvedBy(approvedByUser)
                    .build();
            Transaction savedCreditTxn = transactionRepository.save(creditTxn);

            // PostgreSQL Forensic Audit Store: Simultaneous dual-write for both legs
            dualWriteLedgerAuditService.recordMutationAudit(
                    savedDebitTxn.getTransactionId() != null ? savedDebitTxn.getTransactionId() : 0L,
                    srcId, "DEBIT", amount, srcPrev, srcNew, actorId, clientIp
            );
            dualWriteLedgerAuditService.recordMutationAudit(
                    savedCreditTxn.getTransactionId() != null ? savedCreditTxn.getTransactionId() : 0L,
                    dstId, "CREDIT", amount, dstPrev, dstNew, actorId, clientIp
            );

            Long dstCustId = dstAccount != null && dstAccount.getCustomer() != null ? dstAccount.getCustomer().getCustomerId() : null;

            // Publish domain mutation events for both legs of the transfer
            publishMutationEvent(
                    savedDebitTxn.getTransactionId() != null ? savedDebitTxn.getTransactionId() : 0L,
                    srcId,
                    srcAccount != null ? srcAccount.getAccountNumber() : null,
                    "DEBIT",
                    amount,
                    srcPrev,
                    srcNew,
                    refDebit,
                    srcCustId
            );
            publishMutationEvent(
                    savedCreditTxn.getTransactionId() != null ? savedCreditTxn.getTransactionId() : 0L,
                    dstId,
                    dstAccount != null ? dstAccount.getAccountNumber() : null,
                    "CREDIT",
                    amount,
                    dstPrev,
                    dstNew,
                    refCredit,
                    dstCustId
            );

            // Publish transfer completed event
            publishTransferEvent(
                    ref,
                    srcId,
                    dstId,
                    amount,
                    srcPrev,
                    srcNew,
                    dstPrev,
                    dstNew,
                    srcCustId,
                    dstCustId
            );

            log.info("Atomic transfer completed: ref={}, src={} ({} -> {}), dst={} ({} -> {}), amount={}",
                    ref, srcId, srcPrev, srcNew, dstId, dstPrev, dstNew, amount);

            return TransferResponseDto.builder()
                    .transferReference(ref)
                    .sourceAccountId(srcId)
                    .destinationAccountId(dstId)
                    .amount(amount)
                    .sourcePreviousBalance(srcPrev)
                    .sourceNewBalance(srcNew)
                    .destinationPreviousBalance(dstPrev)
                    .destinationNewBalance(dstNew)
                    .status("COMPLETED")
                    .timestamp(Instant.now())
                    .build();
        } catch (Exception ex) {
            publishTransferFailedEvent(
                    ref != null ? ref : "TRF-FAILED-" + UUID.randomUUID().toString().substring(0, 8),
                    srcId != null ? srcId : 0L,
                    dstId != null ? dstId : 0L,
                    srcCustId,
                    amount,
                    ex.getMessage(),
                    ex.getClass().getSimpleName()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DebitCreditResponseDto mutateDebit(DebitCreditRequestDto request, Long actorId, String clientIp) {
        Long accountId = request.getAccountId();
        BigDecimal amount = request.getAmount();
        String ref = request.getReferenceNo();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Debit amount must be greater than zero.");
        }

        Balance balance = balanceRepository.findByAccountId(accountId)
                .or(() -> balanceRepository.findById(accountId))
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal currentBalance = balance.getAvailableBalance();
        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId, currentBalance, amount);
        }

        BigDecimal newBalance = currentBalance.subtract(amount);
        balance.setAvailableBalance(newBalance);
        balanceRepository.save(balance);

        Account account = balance.getAccount() != null ? balance.getAccount() : Account.builder().accountId(accountId).build();
        if (account != null && account.getStatus() != null && !"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new AccountNotActiveException(accountId, account.getStatus());
        }
        Transaction txn = Transaction.builder()
                .account(account)
                .referenceNo(ref)
                .transactionType("DEBIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .approvedBy(actorId != null ? User.builder().userId(actorId).build() : null)
                .build();
        Transaction savedTxn = transactionRepository.save(txn);
        Long txnId = savedTxn != null ? savedTxn.getTransactionId() : 0L;

        dualWriteLedgerAuditService.recordMutationAudit(
                txnId, accountId, "DEBIT", amount, currentBalance, newBalance, actorId, clientIp
        );

        publishMutationEvent(
                txnId,
                accountId,
                account != null ? account.getAccountNumber() : null,
                "DEBIT",
                amount,
                currentBalance,
                newBalance,
                ref,
                account != null && account.getCustomer() != null ? account.getCustomer().getCustomerId() : null
        );

        return DebitCreditResponseDto.builder()
                .transactionId(txnId)
                .accountId(accountId)
                .transactionType("DEBIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .referenceNo(ref)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DebitCreditResponseDto mutateCredit(DebitCreditRequestDto request, Long actorId, String clientIp) {
        Long accountId = request.getAccountId();
        BigDecimal amount = request.getAmount();
        String ref = request.getReferenceNo();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Credit amount must be greater than zero.");
        }

        Balance balance = balanceRepository.findByAccountId(accountId)
                .or(() -> balanceRepository.findById(accountId))
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal currentBalance = balance.getAvailableBalance();
        BigDecimal newBalance = currentBalance.add(amount);
        balance.setAvailableBalance(newBalance);
        balanceRepository.save(balance);

        Account account = balance.getAccount() != null ? balance.getAccount() : Account.builder().accountId(accountId).build();
        if (account != null && account.getStatus() != null && !"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new AccountNotActiveException(accountId, account.getStatus());
        }
        Transaction txn = Transaction.builder()
                .account(account)
                .referenceNo(ref)
                .transactionType("CREDIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .approvedBy(actorId != null ? User.builder().userId(actorId).build() : null)
                .build();
        Transaction savedTxn = transactionRepository.save(txn);
        Long txnId = savedTxn != null ? savedTxn.getTransactionId() : 0L;

        dualWriteLedgerAuditService.recordMutationAudit(
                txnId, accountId, "CREDIT", amount, currentBalance, newBalance, actorId, clientIp
        );

        publishMutationEvent(
                txnId,
                accountId,
                account != null ? account.getAccountNumber() : null,
                "CREDIT",
                amount,
                currentBalance,
                newBalance,
                ref,
                account != null && account.getCustomer() != null ? account.getCustomer().getCustomerId() : null
        );

        return DebitCreditResponseDto.builder()
                .transactionId(txnId)
                .accountId(accountId)
                .transactionType("CREDIT")
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .referenceNo(ref)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponseDto getAccountBalance(Long accountId) {
        Balance balance = balanceRepository.findReadOnlyByAccountId(accountId)
                .or(() -> balanceRepository.findById(accountId))
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return BalanceResponseDto.builder()
                .accountId(accountId)
                .currency("PHP")
                .availableBalance(balance.getAvailableBalance())
                .asOfTimestamp(Instant.now())
                .isCached(false)
                .build();
    }

    private void publishMutationEvent(
            Long txnId,
            Long accountId,
            String accountNumber,
            String txnType,
            BigDecimal amount,
            BigDecimal prevBalance,
            BigDecimal newBalance,
            String referenceNo,
            Long customerId) {
        if (applicationEventPublisher != null) {
            LedgerMutationEvent event = LedgerMutationEvent.builder()
                    .eventId("evt_" + UUID.randomUUID())
                    .eventType("LEDGER_MUTATION_COMPLETED")
                    .timestamp(Instant.now())
                    .version("1.0")
                    .payload(LedgerMutationEvent.MutationPayload.builder()
                            .transactionId(txnId)
                            .accountId(accountId)
                            .accountNumber(accountNumber)
                            .transactionType(txnType)
                            .amount(amount != null ? amount.setScale(4, RoundingMode.HALF_UP) : null)
                            .previousBalance(prevBalance != null ? prevBalance.setScale(4, RoundingMode.HALF_UP) : null)
                            .newBalance(newBalance != null ? newBalance.setScale(4, RoundingMode.HALF_UP) : null)
                            .referenceNo(referenceNo)
                            .customerId(customerId)
                            .build())
                    .build();
            applicationEventPublisher.publishEvent(event);
            log.debug("Enqueued Spring application event LedgerMutationEvent for account {}", accountId);
        }
    }

    private void publishTransferEvent(
            String transferReference,
            Long srcId,
            Long dstId,
            BigDecimal amount,
            BigDecimal srcPrev,
            BigDecimal srcNew,
            BigDecimal dstPrev,
            BigDecimal dstNew,
            Long srcCustId,
            Long dstCustId) {
        if (applicationEventPublisher != null) {
            LedgerTransferEvent event = LedgerTransferEvent.builder()
                    .eventId("evt_" + UUID.randomUUID())
                    .eventType("LEDGER_TRANSFER_COMPLETED")
                    .timestamp(Instant.now())
                    .version("1.0")
                    .payload(LedgerTransferEvent.TransferPayload.builder()
                            .transferReference(transferReference)
                            .sourceAccountId(srcId)
                            .destinationAccountId(dstId)
                            .amount(amount != null ? amount.setScale(4, RoundingMode.HALF_UP) : null)
                            .sourcePreviousBalance(srcPrev != null ? srcPrev.setScale(4, RoundingMode.HALF_UP) : null)
                            .sourceNewBalance(srcNew != null ? srcNew.setScale(4, RoundingMode.HALF_UP) : null)
                            .destinationPreviousBalance(dstPrev != null ? dstPrev.setScale(4, RoundingMode.HALF_UP) : null)
                            .destinationNewBalance(dstNew != null ? dstNew.setScale(4, RoundingMode.HALF_UP) : null)
                            .sourceCustomerId(srcCustId)
                            .destinationCustomerId(dstCustId)
                            .build())
                    .build();
            applicationEventPublisher.publishEvent(event);
            log.debug("Enqueued Spring application event LedgerTransferEvent for ref {}", transferReference);

            if (outboxService != null) {
                try {
                    outboxService.appendEvent("ledger.transfer.completed.v1", String.valueOf(srcId), event);
                } catch (Exception ex) {
                    log.warn("Could not append to outbox in executeTransfer (continuing with Spring event): {}", ex.getMessage());
                }
            }
        }
    }

    private void publishTransferFailedEvent(
            String transferReference,
            Long srcId,
            Long dstId,
            Long srcCustId,
            BigDecimal amount,
            String failureReason,
            String errorCode) {
        if (applicationEventPublisher != null) {
            LedgerTransferFailedEvent event = LedgerTransferFailedEvent.builder()
                    .eventId("evt_" + UUID.randomUUID())
                    .eventType("LEDGER_TRANSFER_FAILED")
                    .timestamp(Instant.now())
                    .version("1.0")
                    .payload(LedgerTransferFailedEvent.FailedTransferPayload.builder()
                            .transferReference(transferReference)
                            .sourceAccountId(srcId)
                            .destinationAccountId(dstId)
                            .sourceCustomerId(srcCustId)
                            .amount(amount != null ? amount.setScale(4, RoundingMode.HALF_UP) : null)
                            .failureReason(failureReason)
                            .errorCode(errorCode)
                            .build())
                    .build();
            applicationEventPublisher.publishEvent(event);
            log.info("Published LedgerTransferFailedEvent for ref {}: reason={}", transferReference, failureReason);
        }
    }
}