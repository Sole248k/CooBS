package com.group2.fse.ledger_service.service.impl;

import com.group2.fse.ledger_service.dto.*;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Hold;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.entity.User;
import com.group2.fse.ledger_service.exception.DuplicateIdempotencyKeyException;
import com.group2.fse.ledger_service.exception.HoldNotFoundException;
import com.group2.fse.ledger_service.exception.HoldNotReservedException;
import com.group2.fse.ledger_service.posting.OrderedAccountLocks;
import com.group2.fse.ledger_service.posting.PostingEngine;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.HoldRepository;
import com.group2.fse.ledger_service.service.HoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldServiceImpl implements HoldService {

    private static final String CACHE_KEY_PREFIX = "ledger:balance:";
    private static final int JOURNAL_REFERENCE_MAX_LENGTH = 36;

    private final HoldRepository holdRepository;
    private final BalanceRepository balanceRepository;
    private final PostingEngine postingEngine;
    private final com.group2.fse.ledger_service.service.OutboxService outboxService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlaceHoldResponseDto placeHold(PlaceHoldRequestDto request, String idempotencyKey) {
        log.info("Processing place hold request: refNo={}, accountId={}, amount={}",
                request.getReferenceNo(), request.getAccountId(), request.getAmount());

        // Idempotency check via reference number
        Optional<Hold> existingHold = holdRepository.findByReferenceNo(request.getReferenceNo());
        if (existingHold.isPresent()) {
            Hold hold = existingHold.get();
            requireSameHoldRequest(hold, request);
            log.info("Idempotent hit: hold already exists for refNo={}, holdId={}", request.getReferenceNo(), hold.getHoldId());
            Balance balance = balanceRepository.findByAccountId(hold.getAccount().getAccountId()).orElse(null);
            return buildExistingHoldResponse(hold, balance);
        }

        postingEngine.validatePositiveAmount(request.getAmount(), "Hold");

        // Row-level pessimistic write lock
        Balance balance = postingEngine.lockAccount(request.getAccountId(), true);

        // A concurrent request with the same reference may have committed while this one waited
        // for the balance lock, so the check above is repeated now that the lock is held.
        Optional<Hold> racedHold = holdRepository.findByReferenceNo(request.getReferenceNo());
        if (racedHold.isPresent()) {
            Hold hold = racedHold.get();
            requireSameHoldRequest(hold, request);
            log.info("Idempotent hit after balance lock: refNo={}, holdId={}", request.getReferenceNo(), hold.getHoldId());
            return buildExistingHoldResponse(hold, balance);
        }

        postingEngine.validateSufficientFunds(request.getAccountId(), balance.getAvailableBalance(), request.getAmount());

        // Hold debit only reduces availableBalance; ledgerBalance remains intact until capture
        BigDecimal newAvailableBalance = postingEngine.calculateDebit(balance.getAvailableBalance(), request.getAmount());
        balance.setAvailableBalance(newAvailableBalance);
        balanceRepository.save(balance);

        evictBalanceCache(request.getAccountId());

        Hold hold = Hold.builder()
                .account(balance.getAccount())
                .referenceNo(request.getReferenceNo())
                .amount(request.getAmount().setScale(4, RoundingMode.HALF_UP))
                .status("RESERVED")
                .createdAt(LocalDateTime.now())
                .build();
        hold = holdRepository.save(hold);

        log.info("Hold successfully reserved: holdId={}, refNo={}, availBal={}, ledgerBal={}",
                hold.getHoldId(), hold.getReferenceNo(), balance.getAvailableBalance(), balance.getLedgerBalance());

        return PlaceHoldResponseDto.builder()
                .holdId(hold.getHoldId())
                .referenceNo(hold.getReferenceNo())
                .accountId(balance.getAccountId())
                .amount(hold.getAmount().setScale(2, RoundingMode.HALF_UP))
                .status("RESERVED")
                .availableBalance(balance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP))
                .ledgerBalance(balance.getLedgerBalance().setScale(2, RoundingMode.HALF_UP))
                .createdAt(hold.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .build();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CaptureHoldResponseDto captureHold(Long holdId, CaptureHoldRequestDto request, String idempotencyKey) {
        log.info("Processing capture hold request: holdId={}, destinationAccountId={}",
                holdId, request.getDestinationAccountId());

        // Hold row first, balances second: the same order as releaseHold, so the two cannot deadlock
        // and the status check below sees committed state.
        Hold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));

        if ("POSTED".equalsIgnoreCase(hold.getStatus())) {
            log.info("Idempotent hit: hold {} is already captured", holdId);
            return buildExistingCaptureResponse(hold, request);
        }

        if (!"RESERVED".equalsIgnoreCase(hold.getStatus())) {
            throw new HoldNotReservedException(holdId, hold.getStatus());
        }

        Long sourceAccountId = hold.getAccount().getAccountId();
        Long destinationAccountId = request.getDestinationAccountId();

        // Deadlock-free ordered pessimistic locking min(src, dst) then max(src, dst)
        OrderedAccountLocks locks = postingEngine.lockAccountsInOrder(sourceAccountId, destinationAccountId);
        Balance sourceBalance = locks.getSourceBalance();
        Balance destinationBalance = locks.getDestinationBalance();

        BigDecimal amount = hold.getAmount();

        // Source account: availableBalance was already debited at place-hold. Debit ledgerBalance now.
        BigDecimal srcPrevLedger = sourceBalance.getLedgerBalance();
        BigDecimal srcNewLedger = postingEngine.calculateDebit(srcPrevLedger, amount);
        sourceBalance.setLedgerBalance(srcNewLedger);
        balanceRepository.save(sourceBalance);

        // Destination account: credit both available and ledger balance
        BigDecimal dstPrevAvail = destinationBalance.getAvailableBalance();
        BigDecimal dstNewAvail = postingEngine.calculateCredit(dstPrevAvail, amount);
        BigDecimal dstPrevLedger = destinationBalance.getLedgerBalance();
        BigDecimal dstNewLedger = postingEngine.calculateCredit(dstPrevLedger, amount);
        destinationBalance.setAvailableBalance(dstNewAvail);
        destinationBalance.setLedgerBalance(dstNewLedger);
        balanceRepository.save(destinationBalance);

        evictBalanceCache(sourceAccountId);
        evictBalanceCache(destinationAccountId);

        // Double-entry journal records
        String refDebit = legReference(hold.getReferenceNo(), "-D");
        String refCredit = legReference(hold.getReferenceNo(), "-C");
        User approvedByUser = request.getActorId() != null ? User.builder().userId(request.getActorId()).build() : null;

        Transaction debitTxn = postingEngine.recordTransaction(
                sourceBalance.getAccount(),
                refDebit,
                "DEBIT",
                amount,
                sourceBalance.getAvailableBalance().add(amount),
                sourceBalance.getAvailableBalance(),
                "COMPLETED",
                approvedByUser
        );

        Transaction creditTxn = postingEngine.recordTransaction(
                destinationBalance.getAccount(),
                refCredit,
                "CREDIT",
                amount,
                dstPrevAvail,
                dstNewAvail,
                "COMPLETED",
                approvedByUser
        );

        Long debitTxnId = debitTxn != null && debitTxn.getTransactionId() != null ? debitTxn.getTransactionId() : 0L;
        Long creditTxnId = creditTxn != null && creditTxn.getTransactionId() != null ? creditTxn.getTransactionId() : 0L;

        postingEngine.recordAudit(
                debitTxnId, sourceAccountId, "DEBIT", amount,
                sourceBalance.getAvailableBalance().add(amount), sourceBalance.getAvailableBalance(),
                request.getActorId(), request.getClientIp()
        );

        postingEngine.recordAudit(
                creditTxnId, destinationAccountId, "CREDIT", amount,
                dstPrevAvail, dstNewAvail,
                request.getActorId(), request.getClientIp()
        );

        LocalDateTime now = LocalDateTime.now();
        hold.setStatus("POSTED");
        hold.setResolvedAt(now);
        holdRepository.save(hold);

        String auditHash = generateAuditHash(hold.getReferenceNo(), debitTxnId, creditTxnId, amount);

        // Transactional Outbox Append (Day 4: Core Transfer Flow)
        try {
            Long srcCustId = sourceBalance.getAccount() != null && sourceBalance.getAccount().getCustomer() != null
                    ? sourceBalance.getAccount().getCustomer().getCustomerId() : null;
            Long dstCustId = destinationBalance.getAccount() != null && destinationBalance.getAccount().getCustomer() != null
                    ? destinationBalance.getAccount().getCustomer().getCustomerId() : null;

            com.group2.fse.ledger_service.event.LedgerTransferEvent outboxPayload =
                    com.group2.fse.ledger_service.event.LedgerTransferEvent.builder()
                            .eventId("evt_" + java.util.UUID.randomUUID())
                            .eventType("LEDGER_TRANSFER_COMPLETED")
                            .timestamp(java.time.Instant.now())
                            .version("1.0")
                            .payload(com.group2.fse.ledger_service.event.LedgerTransferEvent.TransferPayload.builder()
                                    .transferReference(hold.getReferenceNo())
                                    .sourceAccountId(sourceAccountId)
                                    .destinationAccountId(destinationAccountId)
                                    .amount(amount.setScale(4, RoundingMode.HALF_UP))
                                    .sourcePreviousBalance(srcPrevLedger.setScale(4, RoundingMode.HALF_UP))
                                    .sourceNewBalance(srcNewLedger.setScale(4, RoundingMode.HALF_UP))
                                    .destinationPreviousBalance(dstPrevLedger.setScale(4, RoundingMode.HALF_UP))
                                    .destinationNewBalance(dstNewLedger.setScale(4, RoundingMode.HALF_UP))
                                    .sourceCustomerId(srcCustId)
                                    .destinationCustomerId(dstCustId)
                                    .build())
                            .build();

            outboxService.appendEvent("ledger.transfer.completed.v1", String.valueOf(sourceAccountId), outboxPayload);
        } catch (Exception outboxEx) {
            log.error("Failed to append transactional outbox event for hold {}: {}", holdId, outboxEx.getMessage(), outboxEx);
            throw outboxEx;
        }

        log.info("Hold {} captured successfully: debitTxn={}, creditTxn={}, hash={}",
                holdId, debitTxnId, creditTxnId, auditHash);

        return CaptureHoldResponseDto.builder()
                .holdId(hold.getHoldId())
                .status("POSTED")
                .transferReference(hold.getReferenceNo())
                .debitTransactionId(debitTxnId)
                .creditTransactionId(creditTxnId)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .sourceNewBalance(sourceBalance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP))
                .destinationNewBalance(destinationBalance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP))
                .auditHash(auditHash)
                .timestamp(now.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .build();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReleaseHoldResponseDto releaseHold(Long holdId, ReleaseHoldRequestDto request, String idempotencyKey) {
        log.info("Processing release hold request: holdId={}, reason={}", holdId, request != null ? request.getReason() : "N/A");

        // Hold row first, balance second: the same order as captureHold.
        Hold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));

        Long accountId = hold.getAccount().getAccountId();
        Balance balance = postingEngine.lockAccount(accountId, false);

        if ("CANCELLED".equalsIgnoreCase(hold.getStatus())) {
            log.info("Idempotent hit: hold {} is already released/cancelled", holdId);
            return ReleaseHoldResponseDto.builder()
                    .holdId(hold.getHoldId())
                    .status("CANCELLED")
                    .availableBalance(balance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP))
                    .build();
        }

        if (!"RESERVED".equalsIgnoreCase(hold.getStatus())) {
            throw new HoldNotReservedException(holdId, hold.getStatus());
        }

        // Return reserved amount to availableBalance
        BigDecimal restoredAvail = postingEngine.calculateCredit(balance.getAvailableBalance(), hold.getAmount());
        balance.setAvailableBalance(restoredAvail);
        balanceRepository.save(balance);

        evictBalanceCache(accountId);

        hold.setStatus("CANCELLED");
        hold.setResolvedAt(LocalDateTime.now());
        holdRepository.save(hold);

        log.info("Hold {} released successfully: restored availableBalance={}", holdId, balance.getAvailableBalance());

        return ReleaseHoldResponseDto.builder()
                .holdId(hold.getHoldId())
                .status("CANCELLED")
                .availableBalance(balance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HoldDetailDto getHold(Long holdId) {
        Hold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));

        Balance balance = balanceRepository.findByAccountId(hold.getAccount().getAccountId()).orElse(null);

        return HoldDetailDto.builder()
                .holdId(hold.getHoldId())
                .referenceNo(hold.getReferenceNo())
                .accountId(hold.getAccount().getAccountId())
                .amount(hold.getAmount().setScale(2, RoundingMode.HALF_UP))
                .status(hold.getStatus())
                .availableBalance(balance != null ? balance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP) : null)
                .ledgerBalance(balance != null ? balance.getLedgerBalance().setScale(2, RoundingMode.HALF_UP) : null)
                .createdAt(hold.getCreatedAt() != null ? hold.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT) : null)
                .resolvedAt(hold.getResolvedAt() != null ? hold.getResolvedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT) : null)
                .build();
    }

    /**
     * A replay is only a replay if it asks for the same hold. The same reference with another
     * account or amount is a conflict (409), not an idempotent hit.
     */
    private void requireSameHoldRequest(Hold hold, PlaceHoldRequestDto request) {
        boolean sameAccount = Objects.equals(hold.getAccount().getAccountId(), request.getAccountId());
        boolean sameAmount = request.getAmount() != null && hold.getAmount().compareTo(request.getAmount()) == 0;
        if (!sameAccount || !sameAmount) {
            log.warn("Hold replay mismatch for refNo={}, holdId={}: sameAccount={}, sameAmount={}",
                    hold.getReferenceNo(), hold.getHoldId(), sameAccount, sameAmount);
            throw new DuplicateIdempotencyKeyException(String.format(
                    "Hold reference %s was already used for a different account or amount", hold.getReferenceNo()));
        }
    }

    private PlaceHoldResponseDto buildExistingHoldResponse(Hold hold, Balance balance) {
        return PlaceHoldResponseDto.builder()
                .holdId(hold.getHoldId())
                .referenceNo(hold.getReferenceNo())
                .accountId(hold.getAccount().getAccountId())
                .amount(hold.getAmount().setScale(2, RoundingMode.HALF_UP))
                .status(hold.getStatus())
                .availableBalance(balance != null ? balance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP) : null)
                .ledgerBalance(balance != null ? balance.getLedgerBalance().setScale(2, RoundingMode.HALF_UP) : null)
                .createdAt(hold.getCreatedAt() != null ? hold.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT) : Instant.now().toString())
                .build();
    }

    /**
     * Journal reference for one leg of a capture. TRANSACTION.reference_no is VARCHAR2(36), so a
     * hold reference longer than 34 characters (possible only for holds placed before the DTO
     * limit) is cut to make room for the suffix.
     */
    private String legReference(String referenceNo, String suffix) {
        int maxBaseLength = JOURNAL_REFERENCE_MAX_LENGTH - suffix.length();
        String base = referenceNo.length() > maxBaseLength ? referenceNo.substring(0, maxBaseLength) : referenceNo;
        return base + suffix;
    }

    private void evictBalanceCache(Long accountId) {
        if (redisTemplate != null && accountId != null) {
            try {
                redisTemplate.delete(CACHE_KEY_PREFIX + accountId);
            } catch (Exception e) {
                log.warn("Failed to evict Redis balance cache for account {}: {}", accountId, e.getMessage());
            }
        }
    }

    private String generateAuditHash(String ref, Long debitTxnId, Long creditTxnId, BigDecimal amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%s:%d:%d:%s:%s", ref, debitTxnId, creditTxnId, amount.toPlainString(), Instant.now().toString());
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "hash-" + System.currentTimeMillis();
        }
    }

    private CaptureHoldResponseDto buildExistingCaptureResponse(Hold hold, CaptureHoldRequestDto request) {
        Long sourceAccountId = hold.getAccount().getAccountId();
        Long destinationAccountId = request.getDestinationAccountId();
        Balance sourceBalance = balanceRepository.findByAccountId(sourceAccountId).orElse(null);
        Balance destinationBalance = destinationAccountId != null ? balanceRepository.findByAccountId(destinationAccountId).orElse(null) : null;

        return CaptureHoldResponseDto.builder()
                .holdId(hold.getHoldId())
                .status("POSTED")
                .transferReference(hold.getReferenceNo())
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(hold.getAmount().setScale(2, RoundingMode.HALF_UP))
                .sourceNewBalance(sourceBalance != null ? sourceBalance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP) : null)
                .destinationNewBalance(destinationBalance != null ? destinationBalance.getAvailableBalance().setScale(2, RoundingMode.HALF_UP) : null)
                .auditHash(generateAuditHash(hold.getReferenceNo(), 0L, 0L, hold.getAmount()))
                .timestamp(hold.getResolvedAt() != null ? hold.getResolvedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT) : Instant.now().toString())
                .build();
    }
}
