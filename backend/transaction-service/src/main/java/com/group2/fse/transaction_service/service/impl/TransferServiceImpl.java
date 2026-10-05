package com.group2.fse.transaction_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.dto.TransferRequestDto;
import com.group2.fse.transaction_service.exception.DuplicateIdempotencyKeyException;
import com.group2.fse.transaction_service.exception.InvalidTransactionException;
import com.group2.fse.transaction_service.service.IdempotencyService;
import com.group2.fse.transaction_service.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Task: FIN-303
 * Implementation of TransferService returning immediate 202 tracking response
 * guarded by Redis atomic SETNX idempotency locks and pre-flight validation.
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
            String transactionId = UUID.randomUUID().toString();
            String datePart = DATE_FORMATTER.format(Instant.now());
            String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            String referenceNo = "TX-" + datePart + "-" + randomHex;

            log.info("Initiated transfer: transactionId={}, referenceNo={}, srcAccount={}, dstAccount={}, amount={}",
                    transactionId, referenceNo, request.getSourceAccountId(), request.getDestinationAccountNumber(), request.getAmount());

            TransferInitiateResponseDto response = TransferInitiateResponseDto.builder()
                    .transactionId(transactionId)
                    .referenceNo(referenceNo)
                    .status("INITIATED")
                    .amount(request.getAmount())
                    .currency(request.getCurrency() != null ? request.getCurrency() : "PHP")
                    .createdAt(Instant.now())
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
}
