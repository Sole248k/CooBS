package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.StartReversalSagaRequest;
import com.group2.fse.transaction_service.dto.Money;
import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.entity.TxnStageEvent;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.event.StageEventPublisher;
import com.group2.fse.transaction_service.exception.DuplicateIdempotencyKeyException;
import com.group2.fse.transaction_service.exception.InvalidTransactionException;
import com.group2.fse.transaction_service.exception.TransactionNotFoundException;
import com.group2.fse.transaction_service.exception.TransferRejectedException;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.service.IdempotencyService;
import com.group2.fse.transaction_service.service.ReversalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * T5, the reversal request. It saves a reversal transaction that points at the original and
 * starts the reversal saga (O2). It moves no money and decides nothing about balances: the
 * ledger posts the compensating entries, and the original only becomes REVERSED when the
 * saga reports that it did (see TimelineServiceImpl.recordStage).
 */
@Slf4j
@Service
public class ReversalServiceImpl implements ReversalService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    private final IdempotencyService idempotencyService;
    private final TxnTransactionRepository transactionRepository;
    private final TxnStageEventRepository stageEventRepository;
    private final OrchestrationClient orchestrationClient;
    private final StageEventPublisher stageEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public ReversalServiceImpl(IdempotencyService idempotencyService,
                               TxnTransactionRepository transactionRepository,
                               TxnStageEventRepository stageEventRepository,
                               OrchestrationClient orchestrationClient,
                               StageEventPublisher stageEventPublisher,
                               PlatformTransactionManager transactionManager) {
        this.idempotencyService = idempotencyService;
        this.transactionRepository = transactionRepository;
        this.stageEventRepository = stageEventRepository;
        this.orchestrationClient = orchestrationClient;
        this.stageEventPublisher = stageEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public TransferInitiateResponseDto requestReversal(String transactionIdentifier, String reason,
                                                       String idempotencyKey) {
        TransferServiceImpl.Caller caller = TransferServiceImpl.currentCaller();
        if (!caller.staff()) {
            throw new TransferRejectedException(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED", "Access Denied",
                    "Only a Teller or an Admin can reverse a transfer.");
        }
        String key = TransferServiceImpl.scopedKey(caller, idempotencyKey);
        TxnTransaction original = resolve(transactionIdentifier);

        Optional<TxnTransaction> accepted = transactionRepository.findByIdempotencyKey(key);
        if (accepted.isPresent()) {
            return replay(accepted.get(), original, reason, caller);
        }
        requireReversible(original);

        if (!idempotencyService.tryAcquire(key, LOCK_TTL)) {
            throw new DuplicateIdempotencyKeyException(
                    "A request with this Idempotency-Key is currently being processed. Duplicate submission blocked.");
        }
        try {
            accepted = transactionRepository.findByIdempotencyKey(key);
            if (accepted.isPresent()) {
                return replay(accepted.get(), original, reason, caller);
            }
            TxnTransaction reversal = transactionTemplate.execute(status -> save(original, reason, key));
            stageEventPublisher.publishStage(reversal.getTransactionId(), reversal.getReferenceNo(),
                    1, "INITIATED", "PASSED", "Reversal requested by staff");
            stageEventPublisher.publishStage(reversal.getTransactionId(), reversal.getReferenceNo(),
                    2, "VALIDATED", "PASSED", "Original transfer found and reversible");
            stageEventPublisher.publishStage(reversal.getTransactionId(), reversal.getReferenceNo(),
                    3, "AUTHENTICATED", "PASSED", "Dispatched to orchestrator");
            startSaga(reversal, reason, caller);
            return response(reversal);
        } catch (DataIntegrityViolationException raced) {
            return transactionRepository.findByIdempotencyKey(key)
                    .map(existing -> replay(existing, original, reason, caller))
                    .orElseThrow(() -> raced);
        } finally {
            idempotencyService.release(key);
        }
    }

    /** Full amount, back the way it came. Only a transfer that is posted or still in progress can be reversed. */
    private void requireReversible(TxnTransaction original) {
        if (original.getReversesTransactionId() != null) {
            throw new InvalidTransactionException("A reversal cannot itself be reversed.");
        }
        if ("REVERSED".equalsIgnoreCase(original.getStatus())
                || transactionRepository.existsByReversesTransactionIdAndStatusIn(
                        original.getTransactionId(), List.of("PROCESSING", "POSTED"))) {
            throw new TransferRejectedException(HttpStatus.CONFLICT, "ALREADY_REVERSED", "Already Reversed",
                    "This transfer has already been reversed, or a reversal of it is in progress.");
        }
        if ("FAILED".equalsIgnoreCase(original.getStatus())) {
            throw new InvalidTransactionException("A failed transfer moved no money and cannot be reversed.");
        }
    }

    private TxnTransaction save(TxnTransaction original, String reason, String key) {
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();
        String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);

        TxnTransaction reversal = TxnTransaction.builder()
                .transactionId(transactionId)
                .referenceNo("RV-" + DATE_FORMATTER.format(now) + "-" + randomHex)
                .idempotencyKey(key)
                .channel("TELLER")
                .customerId(original.getCustomerId())
                // The money goes back the way it came.
                .sourceAccountId(original.getDestinationAccountId())
                .destinationAccountId(original.getSourceAccountId())
                .destinationAccountNumber(original.getDestinationAccountNumber())
                .destinationBankCode(original.getDestinationBankCode())
                .amount(original.getAmount())
                .currency(original.getCurrency())
                .category("REVERSAL")
                .remarks(reason != null && !reason.isBlank() ? reason : "Reversal of " + original.getReferenceNo())
                .status("PROCESSING")
                .reversesTransactionId(original.getTransactionId())
                .createdAt(now)
                .build();
        transactionRepository.save(reversal);

        stageEventRepository.saveAll(List.of(
                stage(transactionId, 1, "INITIATED", now),
                stage(transactionId, 2, "VALIDATED", now.plusMillis(10)),
                stage(transactionId, 3, "AUTHENTICATED", now.plusMillis(20))));
        return reversal;
    }

    private static TxnStageEvent stage(UUID transactionId, int stageNo, String stageName, Instant occurredAt) {
        return TxnStageEvent.builder()
                .transactionId(transactionId)
                .stageNo(stageNo)
                .stageName(stageName)
                .outcome("PASSED")
                .occurredAt(occurredAt)
                .build();
    }

    private void startSaga(TxnTransaction reversal, String reason, TransferServiceImpl.Caller caller) {
        try {
            orchestrationClient.triggerReversalSagaAsync(StartReversalSagaRequest.builder()
                    .transactionId(reversal.getTransactionId())
                    .reversesTransactionId(reversal.getReversesTransactionId())
                    .referenceNo(reversal.getReferenceNo())
                    .reason(reason)
                    .actorId(caller.userId())
                    .build(), TransferServiceImpl.correlationId());
        } catch (RuntimeException e) {
            log.error("Could not hand reversal saga start for {} to the background executor: {}",
                    reversal.getTransactionId(), e.getMessage());
        }
    }

    /**
     * The answer the first request got. If that reversal is still waiting, the saga start is
     * sent again: O2 is idempotent on the transaction ID, and this is how a start that was lost
     * gets a second chance, with the staff member who is asking as the actor.
     */
    private TransferInitiateResponseDto replay(TxnTransaction accepted, TxnTransaction original, String reason,
                                               TransferServiceImpl.Caller caller) {
        if (!original.getTransactionId().equals(accepted.getReversesTransactionId())) {
            throw TransferRejectedException.keyReused();
        }
        if ("PROCESSING".equalsIgnoreCase(accepted.getStatus())) {
            startSaga(accepted, accepted.getRemarks() != null ? accepted.getRemarks() : reason, caller);
        }
        TransferInitiateResponseDto response = response(accepted);
        response.setReplayed(true);
        return response;
    }

    private static TransferInitiateResponseDto response(TxnTransaction reversal) {
        return TransferInitiateResponseDto.builder()
                .transactionId(reversal.getTransactionId().toString())
                .referenceNo(reversal.getReferenceNo())
                .status("INITIATED")
                .amount(Money.toContractScale(reversal.getAmount()))
                .currency(reversal.getCurrency())
                .createdAt(reversal.getCreatedAt())
                .timelineUrl("/api/v1/transactions/" + reversal.getTransactionId() + "/timeline")
                .reversesTransactionId(reversal.getReversesTransactionId().toString())
                .build();
    }

    private TxnTransaction resolve(String identifier) {
        if (identifier != null && !identifier.isBlank()) {
            try {
                Optional<TxnTransaction> byId = transactionRepository.findByTransactionId(UUID.fromString(identifier));
                if (byId.isPresent()) {
                    return byId.get();
                }
            } catch (IllegalArgumentException notAUuid) {
                // fall through to the reference number
            }
            Optional<TxnTransaction> byReference = transactionRepository.findByReferenceNo(identifier);
            if (byReference.isPresent()) {
                return byReference.get();
            }
        }
        throw new TransactionNotFoundException("Transaction not found for identifier: " + identifier);
    }
}
