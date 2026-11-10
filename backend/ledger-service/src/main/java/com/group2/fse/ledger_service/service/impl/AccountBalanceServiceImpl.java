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
import com.group2.fse.ledger_service.event.LedgerMutationEvent;
import com.group2.fse.ledger_service.event.LedgerTransferEvent;
import com.group2.fse.ledger_service.event.LedgerTransferFailedEvent;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.posting.OrderedAccountLocks;
import com.group2.fse.ledger_service.posting.PostingEngine;
import com.group2.fse.ledger_service.posting.PostingLegResult;
import com.group2.fse.ledger_service.posting.PostingTransferResult;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.service.AccountBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Core Balance Mutation Engine coordinating Oracle Master SoR and PostgreSQL Forensic Audit Store.
 * Locking, balance arithmetic, journal entries and the audit dual-write are delegated to PostingEngine,
 * which is the only writer of a Balance. This class validates, publishes events and maps results.
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
    private final PostingEngine postingEngine;
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

        PostingLegResult leg;
        try {
            leg = postingEngine.postDebit(accountId, amount, referenceNo, actorId, clientIp, false);
        } catch (AccountNotFoundException ex) {
            return MutationResult.failure(accountId, referenceNo, "Account balance record not found.");
        } catch (InsufficientFundsException ex) {
            return MutationResult.failure(accountId, referenceNo, "Insufficient funds for debit operation.");
        }

        publishMutationEvent(leg);

        return MutationResult.success(accountId, referenceNo, leg.getTransactionId(),
                leg.getPreviousBalance(), leg.getNewBalance(), amount);
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

        PostingLegResult leg;
        try {
            leg = postingEngine.postCredit(accountId, amount, referenceNo, actorId, clientIp, false);
        } catch (AccountNotFoundException ex) {
            return MutationResult.failure(accountId, referenceNo, "Account balance record not found.");
        }

        publishMutationEvent(leg);

        return MutationResult.success(accountId, referenceNo, leg.getTransactionId(),
                leg.getPreviousBalance(), leg.getNewBalance(), amount);
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
            postingEngine.validatePositiveAmount(amount, "Transfer");

            // Locked here first so a transfer that fails later still reports the source customer;
            // postTransfer re-reads the same two rows inside this transaction.
            OrderedAccountLocks locks = postingEngine.lockAccountsInOrder(srcId, dstId);
            Account lockedSource = locks.getSourceBalance().getAccount();
            if (lockedSource != null && lockedSource.getCustomer() != null) {
                srcCustId = lockedSource.getCustomer().getCustomerId();
            }

            PostingTransferResult result = postingEngine.postTransfer(srcId, dstId, amount, ref, actorId, clientIp);

            BigDecimal srcPrev = result.getSourcePreviousBalance();
            BigDecimal srcNew = result.getSourceNewBalance();
            BigDecimal dstPrev = result.getDestinationPreviousBalance();
            BigDecimal dstNew = result.getDestinationNewBalance();
            Long dstCustId = result.getDestinationCustomerId();
            Transaction debitTxn = result.getDebitTransaction();
            Transaction creditTxn = result.getCreditTransaction();

            // Publish domain mutation events for both legs of the transfer
            publishMutationEvent(
                    debitTxn != null && debitTxn.getTransactionId() != null ? debitTxn.getTransactionId() : 0L,
                    srcId,
                    result.getSourceAccountNumber(),
                    "DEBIT",
                    amount,
                    srcPrev,
                    srcNew,
                    debitTxn != null ? debitTxn.getReferenceNo() : null,
                    srcCustId
            );
            publishMutationEvent(
                    creditTxn != null && creditTxn.getTransactionId() != null ? creditTxn.getTransactionId() : 0L,
                    dstId,
                    result.getDestinationAccountNumber(),
                    "CREDIT",
                    amount,
                    dstPrev,
                    dstNew,
                    creditTxn != null ? creditTxn.getReferenceNo() : null,
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
        PostingLegResult leg = postingEngine.postDebit(
                request.getAccountId(), request.getAmount(), request.getReferenceNo(), actorId, clientIp, true);

        publishMutationEvent(leg);
        return toResponse(leg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DebitCreditResponseDto mutateCredit(DebitCreditRequestDto request, Long actorId, String clientIp) {
        PostingLegResult leg = postingEngine.postCredit(
                request.getAccountId(), request.getAmount(), request.getReferenceNo(), actorId, clientIp, true);

        publishMutationEvent(leg);
        return toResponse(leg);
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponseDto getAccountBalance(Long accountId) {
        Balance balance = balanceRepository.findReadOnlyByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return BalanceResponseDto.builder()
                .accountId(accountId)
                .currency("PHP")
                .availableBalance(balance.getAvailableBalance())
                .asOfTimestamp(Instant.now())
                .isCached(false)
                .build();
    }

    private DebitCreditResponseDto toResponse(PostingLegResult leg) {
        return DebitCreditResponseDto.builder()
                .transactionId(leg.getTransactionId())
                .accountId(leg.getAccountId())
                .transactionType(leg.getTransactionType())
                .amount(leg.getAmount())
                .previousBalance(leg.getPreviousBalance())
                .newBalance(leg.getNewBalance())
                .status("COMPLETED")
                .referenceNo(leg.getReferenceNo())
                .timestamp(Instant.now())
                .build();
    }

    private void publishMutationEvent(PostingLegResult leg) {
        publishMutationEvent(
                leg.getTransactionId(),
                leg.getAccountId(),
                leg.getAccountNumber(),
                leg.getTransactionType(),
                leg.getAmount(),
                leg.getPreviousBalance(),
                leg.getNewBalance(),
                leg.getReferenceNo(),
                leg.getCustomerId()
        );
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
            // Published once. The outbox row commits with the posting and the relay sends it; the
            // direct send is only the fallback, because both together put the event on the topic twice.
            if (outboxService != null) {
                try {
                    outboxService.appendEvent("ledger.transfer.completed.v1", String.valueOf(srcId), event);
                    return;
                } catch (Exception ex) {
                    log.warn("Could not append to outbox in executeTransfer (sending the event directly): {}", ex.getMessage());
                }
            }
            applicationEventPublisher.publishEvent(event);
            log.debug("Enqueued Spring application event LedgerTransferEvent for ref {}", transferReference);
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