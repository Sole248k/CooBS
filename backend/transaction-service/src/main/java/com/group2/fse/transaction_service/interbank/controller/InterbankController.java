package com.group2.fse.transaction_service.interbank.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.interbank.dto.InterbankAccountLookupResponse;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferRequest;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferResponse;
import com.group2.fse.transaction_service.interbank.security.InterbankAuthException;
import com.group2.fse.transaction_service.interbank.security.InterbankAuthValidator;
import com.group2.fse.transaction_service.interbank.service.InterbankService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Controller exposing the Interbank Inbound Remittance Webhook and Status APIs
 * matching INTERBANK_TRANSFER_PROPOSAL.md (FIN-407).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/interbank")
@RequiredArgsConstructor
public class InterbankController {

    private final InterbankService interbankService;
    private final InterbankAuthValidator authValidator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Endpoint 1: Receive an inbound transfer.
     * Mapped to both standard /transfers and Jira FIN-407 /transfers/inbound.
     */
    @PostMapping({"/transfers", "/transfers/inbound"})
    public ResponseEntity<InterbankTransferResponse> receiveInboundTransfer(
            @RequestHeader(value = "X-Bank-Code", required = false) String bankCode,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestBody(required = false) String rawBody) {

        log.info("Received inbound interbank transfer request from bankCode={}, timestamp={}", bankCode, timestamp);

        // 1. Authenticate request via signature or API key
        authValidator.validate(bankCode, timestamp, signature, apiKey, rawBody != null ? rawBody : "");

        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.badRequest().body(
                    InterbankTransferResponse.rejected("unknown", "INVALID_REQUEST", "Request body is empty.", Instant.now().toString())
            );
        }

        // 2. Deserialize request
        InterbankTransferRequest request;
        try {
            request = objectMapper.readValue(rawBody, InterbankTransferRequest.class);
        } catch (JsonProcessingException ex) {
            log.warn("Malformed JSON in interbank transfer: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(
                    InterbankTransferResponse.rejected("unknown", "INVALID_REQUEST", "Malformed JSON payload.", Instant.now().toString())
            );
        }

        // 3. Bean Validation
        Set<ConstraintViolation<InterbankTransferRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String firstError = violations.iterator().next().getMessage();
            log.warn("Validation failed for interbank transfer: {}", firstError);
            return ResponseEntity.badRequest().body(
                    InterbankTransferResponse.rejected(
                            request.getMessageId() != null ? request.getMessageId() : "unknown",
                            "INVALID_REQUEST",
                            firstError,
                            Instant.now().toString())
            );
        }

        // 4. Verify sourceBankCode matches X-Bank-Code header if provided
        if (bankCode != null && !bankCode.isBlank() && !bankCode.equalsIgnoreCase(request.getSourceBankCode())) {
            log.warn("sourceBankCode '{}' does not match X-Bank-Code '{}'", request.getSourceBankCode(), bankCode);
            return ResponseEntity.badRequest().body(
                    InterbankTransferResponse.rejected(
                            request.getMessageId(),
                            "INVALID_REQUEST",
                            "sourceBankCode must match X-Bank-Code header.",
                            Instant.now().toString())
            );
        }

        // 5. Process transfer
        InterbankTransferResponse response = interbankService.processInboundTransfer(request);

        if ("ACCEPTED".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
    }

    /**
     * Endpoint 2: Query transfer status by messageId.
     */
    @GetMapping("/transfers/{messageId}")
    public ResponseEntity<InterbankTransferResponse> getTransferStatus(
            @PathVariable String messageId,
            @RequestHeader(value = "X-Bank-Code", required = false) String bankCode,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {

        log.info("Received transfer status enquiry for messageId={}, bankCode={}", messageId, bankCode);

        // Authenticate request (empty body for GET)
        authValidator.validate(bankCode, timestamp, signature, apiKey, "");

        InterbankTransferResponse response = interbankService.getTransferStatus(messageId);

        if ("NOT_FOUND".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } else if ("ACCEPTED".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
    }

    /**
     * Endpoint 3: Check account and return masked name.
     */
    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<InterbankAccountLookupResponse> checkAccount(
            @PathVariable String accountNumber,
            @RequestHeader(value = "X-Bank-Code", required = false) String bankCode,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {

        log.info("Received account check for accountNumber={}, bankCode={}", accountNumber, bankCode);

        // Authenticate request (empty body for GET)
        authValidator.validate(bankCode, timestamp, signature, apiKey, "");

        InterbankAccountLookupResponse response = interbankService.checkAccount(accountNumber);

        if (!response.isExists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(InterbankAuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(InterbankAuthException ex) {
        log.warn("Interbank authentication failed: code={}, message={}", ex.getReasonCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(Map.of(
                "status", "REJECTED",
                "reasonCode", ex.getReasonCode(),
                "reasonMessage", ex.getMessage()
        ));
    }
}
