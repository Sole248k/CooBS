package com.group2.fse.ledger_service.reversal.service.impl;

import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.entity.User;
import com.group2.fse.ledger_service.exception.AccountNotActiveException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.exception.TransactionAlreadyReversedException;
import com.group2.fse.ledger_service.exception.TransactionNotFoundException;
import com.group2.fse.ledger_service.posting.OrderedAccountLocks;
import com.group2.fse.ledger_service.posting.PostingEngine;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.reversal.dto.ReversalEntryDto;
import com.group2.fse.ledger_service.reversal.dto.ReversalRequestDto;
import com.group2.fse.ledger_service.reversal.dto.ReversalResponseDto;
import com.group2.fse.ledger_service.reversal.service.ReversalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core Ledger Reversal Service implementation adhering to double-entry immutable accounting (Handshake H3 L7).
 * Consumes Wayne's PostingEngine for deadlock-free pessimistic locking, balance arithmetic,
 * and dual-write forensic audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalServiceImpl implements ReversalService {

    private final PostingEngine postingEngine;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public ReversalResponseDto processReversal(ReversalRequestDto request, Long actorId, String clientIp) {
        String originalRef = request.getOriginalReferenceNo() != null ? request.getOriginalReferenceNo().trim() : "";
        if (originalRef.isBlank()) {
            throw new InvalidTransactionException("Original reference number is required for reversal");
        }

        String reversalRef = request.getReversalReferenceNo() != null && !request.getReversalReferenceNo().isBlank()
                ? request.getReversalReferenceNo().trim()
                : "RV-" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Long effectiveActorId = request.getActorId() != null ? request.getActorId() : actorId;
        log.info("Processing ledger reversal: originalRef={}, reversalRef={}, actorId={}",
                originalRef, reversalRef, effectiveActorId);

        // 1. A repeat of a reversal that was already applied returns what was stored. The caller
        //    retries when it got no answer the first time, and that must not read as a failure.
        List<Transaction> storedReversal = transactionRepository.findByOriginalReferenceNo(reversalRef);
        if (!storedReversal.isEmpty()) {
            return storedResult(storedReversal, originalRef, reversalRef);
        }

        // 2. Fetch the original posting: the reference itself, or its debit and credit legs
        List<Transaction> originalTxns = transactionRepository.findByOriginalReferenceNo(originalRef);

        if (originalTxns.isEmpty()) {
            throw new TransactionNotFoundException(originalRef);
        }

        // 3. Immutability & Re-reversal verification: Check if already reversed or is already a reversal
        for (Transaction txn : originalTxns) {
            if (txn.getTransactionType() != null && txn.getTransactionType().startsWith("REVERSAL_")) {
                throw new InvalidTransactionException("Cannot reverse a reversal transaction: " + originalRef);
            }
            if (transactionRepository.existsByReversesTransaction_TransactionId(txn.getTransactionId())) {
                throw new TransactionAlreadyReversedException(originalRef);
            }
        }

        // 4. Distinguish between two-legged transfer vs single-leg transaction
        Transaction debitLeg = null;
        Transaction creditLeg = null;

        for (Transaction txn : originalTxns) {
            if ("DEBIT".equalsIgnoreCase(txn.getTransactionType())) {
                debitLeg = txn;
            } else if ("CREDIT".equalsIgnoreCase(txn.getTransactionType())) {
                creditLeg = txn;
            }
        }

        List<ReversalEntryDto> entries = new ArrayList<>();
        BigDecimal totalReversedAmount = BigDecimal.ZERO;
        String currency = "PHP";

        if (debitLeg != null && creditLeg != null) {
            // Case A: Transfer Reversal (Debited Source & Credited Destination)
            entries = reverseTransfer(debitLeg, creditLeg, reversalRef, effectiveActorId, clientIp);
            totalReversedAmount = debitLeg.getAmount();
            if (debitLeg.getAccount() != null && debitLeg.getAccount().getCurrency() != null) {
                currency = debitLeg.getAccount().getCurrency();
            }
        } else {
            // Case B: Single-leg Reversal (Single debit or single credit)
            Transaction singleTxn = originalTxns.get(0);
            entries = reverseSingleLeg(singleTxn, reversalRef, effectiveActorId, clientIp);
            totalReversedAmount = singleTxn.getAmount();
            if (singleTxn.getAccount() != null && singleTxn.getAccount().getCurrency() != null) {
                currency = singleTxn.getAccount().getCurrency();
            }
        }

        return ReversalResponseDto.builder()
                .reversalReferenceNo(reversalRef)
                .originalReferenceNo(originalRef)
                .status("POSTED")
                .entries(entries)
                .timestamp(Instant.now())
                .reversalId(reversalRef)
                .originalTransactionId(originalRef)
                .reversedAmount(totalReversedAmount != null ? totalReversedAmount.setScale(2, RoundingMode.HALF_UP) : null)
                .currency(currency)
                .build();
    }

    /**
     * The result of a reversal that is already in the journal. The same reversal reference for
     * a different original is a conflict (409), not a repeat.
     */
    private ReversalResponseDto storedResult(List<Transaction> stored, String originalRef, String reversalRef) {
        Set<String> originalLegs = TransactionRepository.legReferences(originalRef);
        List<ReversalEntryDto> entries = new ArrayList<>();
        for (Transaction txn : stored) {
            Transaction reversed = txn.getReversesTransaction();
            if (reversed == null || !originalLegs.contains(reversed.getReferenceNo())) {
                throw new TransactionAlreadyReversedException(reversalRef);
            }
            entries.add(ReversalEntryDto.builder()
                    .transactionId(txn.getTransactionId())
                    .accountId(txn.getAccount() != null ? txn.getAccount().getAccountId() : null)
                    .transactionType(txn.getTransactionType())
                    .amount(txn.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .newBalance(txn.getNewBalance() != null ? txn.getNewBalance().setScale(2, RoundingMode.HALF_UP) : null)
                    .build());
        }
        Transaction first = stored.get(0);
        log.info("Reversal {} of {} was already applied; returning the stored result", reversalRef, originalRef);
        return ReversalResponseDto.builder()
                .reversalReferenceNo(reversalRef)
                .originalReferenceNo(originalRef)
                .status("POSTED")
                .entries(entries)
                .timestamp(Instant.now())
                .reversalId(reversalRef)
                .originalTransactionId(originalRef)
                .reversedAmount(first.getAmount().setScale(2, RoundingMode.HALF_UP))
                .currency(first.getAccount() != null && first.getAccount().getCurrency() != null
                        ? first.getAccount().getCurrency() : "PHP")
                .build();
    }

    private List<ReversalEntryDto> reverseTransfer(
            Transaction originalDebitLeg,
            Transaction originalCreditLeg,
            String reversalRef,
            Long actorId,
            String clientIp) {

        Account senderAccount = originalDebitLeg.getAccount();
        Account recipientAccount = originalCreditLeg.getAccount();
        Long senderAccountId = senderAccount.getAccountId();
        Long recipientAccountId = recipientAccount.getAccountId();
        BigDecimal amount = originalDebitLeg.getAmount();

        postingEngine.validatePositiveAmount(amount, "Reversal");

        // Deadlock-free lock acquisition: recipient locked then sender (ordered by min/max ID)
        OrderedAccountLocks locks = postingEngine.lockAccountsInOrder(recipientAccountId, senderAccountId);
        Balance lockedRecipientBal = locks.getSourceBalance();
        Balance lockedSenderBal = locks.getDestinationBalance();

        // Validate recipient has sufficient funds to absorb reversal debit
        postingEngine.validateSufficientFunds(recipientAccountId, lockedRecipientBal.getAvailableBalance(), amount);

        // Apply balance math
        BigDecimal recipientPrev = lockedRecipientBal.getAvailableBalance();
        BigDecimal senderPrev = lockedSenderBal.getAvailableBalance();

        Balance updatedRecipientBal = postingEngine.applyDebit(lockedRecipientBal, amount);
        Balance updatedSenderBal = postingEngine.applyCredit(lockedSenderBal, amount);

        BigDecimal recipientNew = updatedRecipientBal.getAvailableBalance();
        BigDecimal senderNew = updatedSenderBal.getAvailableBalance();

        // Construct unique references (<= 36 chars)
        String refCredit = reversalRef.length() > 33 ? reversalRef.substring(0, 33) + "-C" : reversalRef + "-C";
        String refDebit = reversalRef.length() > 33 ? reversalRef.substring(0, 33) + "-D" : reversalRef + "-D";

        User approvedByUser = actorId != null ? User.builder().userId(actorId).build() : null;

        // Record REVERSAL_CREDIT leg (Restores sender funds)
        Transaction revCreditTxn = Transaction.builder()
                .account(senderAccount)
                .referenceNo(refCredit)
                .transactionType("REVERSAL_CREDIT")
                .amount(amount)
                .previousBalance(senderPrev)
                .newBalance(senderNew)
                .status("COMPLETED")
                .reversesTransaction(originalDebitLeg)
                .approvedBy(approvedByUser)
                .build();
        revCreditTxn = transactionRepository.save(revCreditTxn);

        // Record REVERSAL_DEBIT leg (Deducts recipient funds)
        Transaction revDebitTxn = Transaction.builder()
                .account(recipientAccount)
                .referenceNo(refDebit)
                .transactionType("REVERSAL_DEBIT")
                .amount(amount)
                .previousBalance(recipientPrev)
                .newBalance(recipientNew)
                .status("COMPLETED")
                .reversesTransaction(originalCreditLeg)
                .approvedBy(approvedByUser)
                .build();
        revDebitTxn = transactionRepository.save(revDebitTxn);

        // Synchronous Dual-Write to PostgreSQL Forensic Audit Store via PostingEngine
        postingEngine.recordAudit(
                revCreditTxn.getTransactionId(),
                senderAccountId,
                "REVERSAL_CREDIT",
                amount,
                senderPrev,
                senderNew,
                actorId,
                clientIp
        );
        postingEngine.recordAudit(
                revDebitTxn.getTransactionId(),
                recipientAccountId,
                "REVERSAL_DEBIT",
                amount,
                recipientPrev,
                recipientNew,
                actorId,
                clientIp
        );

        log.info("Completed transfer reversal: revRef={}, senderAcct={}, recipientAcct={}, amount={}",
                reversalRef, senderAccountId, recipientAccountId, amount);

        return List.of(
                ReversalEntryDto.builder()
                        .transactionId(revCreditTxn.getTransactionId())
                        .accountId(senderAccountId)
                        .transactionType("REVERSAL_CREDIT")
                        .amount(amount.setScale(2, RoundingMode.HALF_UP))
                        .newBalance(senderNew.setScale(2, RoundingMode.HALF_UP))
                        .build(),
                ReversalEntryDto.builder()
                        .transactionId(revDebitTxn.getTransactionId())
                        .accountId(recipientAccountId)
                        .transactionType("REVERSAL_DEBIT")
                        .amount(amount.setScale(2, RoundingMode.HALF_UP))
                        .newBalance(recipientNew.setScale(2, RoundingMode.HALF_UP))
                        .build()
        );
    }

    private List<ReversalEntryDto> reverseSingleLeg(
            Transaction originalTxn,
            String reversalRef,
            Long actorId,
            String clientIp) {

        Account account = originalTxn.getAccount();
        Long accountId = account.getAccountId();
        BigDecimal amount = originalTxn.getAmount();
        String originalType = originalTxn.getTransactionType();

        postingEngine.validatePositiveAmount(amount, "Reversal");
        Balance lockedBalance = postingEngine.lockAccount(accountId, true);
        BigDecimal currentBalance = lockedBalance.getAvailableBalance();

        String reversalType;
        BigDecimal newBalance;
        Balance updatedBalance;

        if ("DEBIT".equalsIgnoreCase(originalType)) {
            // Reversing a debit restores the funds -> REVERSAL_CREDIT
            reversalType = "REVERSAL_CREDIT";
            updatedBalance = postingEngine.applyCredit(lockedBalance, amount);
            newBalance = updatedBalance.getAvailableBalance();
        } else if ("CREDIT".equalsIgnoreCase(originalType)) {
            // Reversing a credit takes back funds -> REVERSAL_DEBIT (requires sufficiency check)
            reversalType = "REVERSAL_DEBIT";
            postingEngine.validateSufficientFunds(accountId, currentBalance, amount);
            updatedBalance = postingEngine.applyDebit(lockedBalance, amount);
            newBalance = updatedBalance.getAvailableBalance();
        } else {
            throw new InvalidTransactionException("Unsupported transaction type for reversal: " + originalType);
        }

        User approvedByUser = actorId != null ? User.builder().userId(actorId).build() : null;

        Transaction revTxn = Transaction.builder()
                .account(account)
                .referenceNo(reversalRef)
                .transactionType(reversalType)
                .amount(amount)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .status("COMPLETED")
                .reversesTransaction(originalTxn)
                .approvedBy(approvedByUser)
                .build();
        revTxn = transactionRepository.save(revTxn);

        postingEngine.recordAudit(
                revTxn.getTransactionId(),
                accountId,
                reversalType,
                amount,
                currentBalance,
                newBalance,
                actorId,
                clientIp
        );

        return List.of(
                ReversalEntryDto.builder()
                        .transactionId(revTxn.getTransactionId())
                        .accountId(accountId)
                        .transactionType(reversalType)
                        .amount(amount.setScale(2, RoundingMode.HALF_UP))
                        .newBalance(newBalance.setScale(2, RoundingMode.HALF_UP))
                        .build()
        );
    }
}
