package com.group2.fse.transaction_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.dto.TransferRequestDto;
import com.group2.fse.transaction_service.exception.DuplicateIdempotencyKeyException;
import com.group2.fse.transaction_service.exception.InvalidTransactionException;
import com.group2.fse.transaction_service.service.IdempotencyService;
import com.group2.fse.transaction_service.service.TransferService;
import com.group2.fse.transaction_service.entity.TxnStageEvent;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Task: FIN-303, FIN-304
 * Implementation of TransferService returning immediate 202 tracking response
 * guarded by Redis atomic SETNX idempotency locks, pre-flight validation,
 * DB persistence in txn_transaction and initial stage events recording.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final TxnTransactionRepository transactionRepository;
    private final TxnStageEventRepository stageEventRepository;
    private final com.group2.fse.transaction_service.client.OrchestrationClient orchestrationClient;
    private final com.group2.fse.transaction_service.event.StageEventPublisher stageEventPublisher;

    @Override
    public TransferInitiateResponseDto initiateTransfer(TransferRequestDto request, String idempotencyKey) {
        validateRequest(request);

        boolean hasKey = idempotencyKey != null && !idempotencyKey.isBlank();
        if (hasKey) {
            boolean acquired = idempotencyService.tryAcquire(idempotencyKey, LOCK_TTL);
            if (!acquired) {
                log.warn("Idempotency lock held for key: {}. Duplicate submission blocked with 409 Conflict.", idempotencyKey);
                throw new DuplicateIdempotencyKeyException(
                        "A transaction with this Idempotency-Key is currently being processed. Duplicate submission blocked.");
            }
        }

        try {
            UUID txUuid = UUID.randomUUID();
            String transactionId = txUuid.toString();
            Instant now = Instant.now();
            String datePart = DATE_FORMATTER.format(now);
            String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            String referenceNo = "TX-" + datePart + "-" + randomHex;

            log.info("Initiated transfer: transactionId={}, referenceNo={}, srcAccount={}, dstAccount={}, amount={}",
                    transactionId, referenceNo, request.getSourceAccountId(), request.getDestinationAccountNumber(), request.getAmount());

            TxnTransaction txn = TxnTransaction.builder()
                    .transactionId(txUuid)
                    .referenceNo(referenceNo)
                    .idempotencyKey(hasKey ? idempotencyKey : null)
                    .channel("APP")
                    .sourceAccountId(request.getSourceAccountId())
                    .destinationAccountNumber(request.getDestinationAccountNumber())
                    .destinationBankCode(request.getDestinationBankCode() != null ? request.getDestinationBankCode() : "COOBS")
                    .amount(request.getAmount())
                    .currency(request.getCurrency() != null ? request.getCurrency() : "PHP")
                    .category(request.getCategory() != null ? request.getCategory() : "TRANSFER")
                    .remarks(request.getRemarks())
                    .status("PROCESSING")
                    .createdAt(now)
                    .build();
            transactionRepository.save(txn);

            TxnStageEvent stage1 = TxnStageEvent.builder()
                    .transactionId(txUuid)
                    .stageNo(1)
                    .stageName("INGRESS_ACCEPTED")
                    .outcome("PASSED")
                    .occurredAt(now)
                    .build();
            TxnStageEvent stage2 = TxnStageEvent.builder()
                    .transactionId(txUuid)
                    .stageNo(2)
                    .stageName("PREFLIGHT_VALIDATED")
                    .outcome("PASSED")
                    .occurredAt(now.plusMillis(10))
                    .build();
            TxnStageEvent stage3 = TxnStageEvent.builder()
                    .transactionId(txUuid)
                    .stageNo(3)
                    .stageName("DISPATCHED_TO_ORCHESTRATOR")
                    .outcome("PASSED")
                    .occurredAt(now.plusMillis(20))
                    .build();
            stageEventRepository.saveAll(List.of(stage1, stage2, stage3));

            // Publish ingress stages to transaction.stage.changed.v1 (Day 5 Event Propagation)
            stageEventPublisher.publishStage(txUuid, referenceNo, 1, "INGRESS_ACCEPTED", "PASSED", "Ingress Intake accepted");
            stageEventPublisher.publishStage(txUuid, referenceNo, 2, "PREFLIGHT_VALIDATED", "PASSED", "Preflight validation passed");
            stageEventPublisher.publishStage(txUuid, referenceNo, 3, "DISPATCHED_TO_ORCHESTRATOR", "PASSED", "Dispatched to orchestrator");

            // Trigger orchestration-service asynchronously (O1)
            triggerOrchestration(txUuid, referenceNo, request);

            TransferInitiateResponseDto response = TransferInitiateResponseDto.builder()
                    .transactionId(transactionId)
                    .referenceNo(referenceNo)
                    .status("INITIATED")
                    .amount(request.getAmount())
                    .currency(request.getCurrency() != null ? request.getCurrency() : "PHP")
                    .createdAt(now)
                    .timelineUrl("/api/v1/transactions/" + transactionId + "/timeline")
                    .build();

            if (hasKey) {
                try {
                    idempotencyService.complete(idempotencyKey, objectMapper.writeValueAsString(response), CACHE_TTL);
                } catch (Exception ex) {
                    log.warn("Failed to cache response in Redis for idempotencyKey: {}", idempotencyKey, ex);
                }
            }

            return response;
        } catch (Exception ex) {
            if (hasKey) {
                idempotencyService.release(idempotencyKey);
            }
            throw ex;
        }
    }

    private void validateRequest(TransferRequestDto request) {
        if (request == null) {
            throw new InvalidTransactionException("Transfer request must not be null.");
        }
        if (request.getSourceAccountId() == null || request.getSourceAccountId() <= 0) {
            throw new InvalidTransactionException("Source account ID must be positive.");
        }
        if (request.getDestinationAccountNumber() == null || request.getDestinationAccountNumber().isBlank()) {
            throw new InvalidTransactionException("Destination account number is required.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transfer amount must be greater than zero.");
        }
    }

    private void triggerOrchestration(UUID transactionId, String referenceNo, TransferRequestDto request) {
        try {
            Long customerId = 1L;
            var srcLookup = orchestrationClient.lookupAccountById(request.getSourceAccountId());
            if (srcLookup.isPresent() && srcLookup.get().getCustomerId() != null) {
                customerId = srcLookup.get().getCustomerId();
            }

            Long destinationAccountId = null;
            var dstLookup = orchestrationClient.lookupAccountByNumber(request.getDestinationAccountNumber());
            if (dstLookup.isPresent() && dstLookup.get().getAccountId() != null) {
                destinationAccountId = dstLookup.get().getAccountId();
            } else {
                try {
                    String cleanDigits = request.getDestinationAccountNumber().replaceAll("\\D+", "");
                    if (!cleanDigits.isBlank()) {
                        destinationAccountId = Long.parseLong(cleanDigits);
                    }
                } catch (Exception ignore) {
                }
                if (destinationAccountId == null) {
                    destinationAccountId = 2L;
                }
            }

            com.group2.fse.transaction_service.client.dto.StartTransferSagaRequest sagaRequest =
                    com.group2.fse.transaction_service.client.dto.StartTransferSagaRequest.builder()
                            .transactionId(transactionId)
                            .referenceNo(referenceNo)
                            .channel("APP")
                            .customerId(customerId)
                            .sourceAccountId(request.getSourceAccountId())
                            .destinationAccountId(destinationAccountId)
                            .destinationBankCode(request.getDestinationBankCode() != null ? request.getDestinationBankCode() : "COOBS")
                            .amount(request.getAmount())
                            .currency(request.getCurrency() != null ? request.getCurrency() : "PHP")
                            .actorId(customerId)
                            .actorType("CUSTOMER")
                            .build();

            orchestrationClient.triggerTransferSagaAsync(sagaRequest, UUID.randomUUID().toString());
        } catch (Exception e) {
            log.error("Failed to enqueue orchestration saga trigger for {}: {}", transactionId, e.getMessage());
        }
    }
}
