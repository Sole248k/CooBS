package com.group2.fse.core_mock.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * Handshake H3 Core Ledger Mock Controller.
 * Offline fixture for Carl (Dev 1)'s orchestration-service Transfer Saga.
 * Simulates Handshake H3 endpoints (L3, L4, L5, L6, L7).
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/ledger")
public class MockLedgerController {

    private final AtomicLong holdIdCounter = new AtomicLong(9001);
    private final AtomicLong txnIdCounter = new AtomicLong(5001);

    /**
     * L3: Place Hold (POST /internal/v1/ledger/holds)
     */
    @PostMapping("/holds")
    public ResponseEntity<?> placeHold(@RequestBody Map<String, Object> request) {
        log.info("[core-mock] Received place hold request: {}", request);

        Object accountIdObj = request.get("accountId");
        String accountIdStr = accountIdObj != null ? accountIdObj.toString() : "";

        // Trigger 1: Insufficient funds test trigger (Account starts with 0999 or 999)
        if (accountIdStr.startsWith("0999") || accountIdStr.startsWith("999")) {
            log.warn("[core-mock] Simulating INSUFFICIENT_FUNDS for accountId: {}", accountIdStr);
            Map<String, Object> errorProblem = Map.of(
                    "type", "https://api.corebank.local/errors/INSUFFICIENT_FUNDS",
                    "title", "Insufficient Funds",
                    "status", 422,
                    "detail", "Available balance is lower than the requested amount.",
                    "errorCode", "INSUFFICIENT_FUNDS"
            );
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorProblem);
        }

        // Trigger 2: Timeout / circuit-breaker test trigger (Account starts with 0777 or 777)
        if (accountIdStr.startsWith("0777") || accountIdStr.startsWith("777")) {
            log.warn("[core-mock] Simulating 5000ms delay for timeout test on accountId: {}", accountIdStr);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }

        long holdId = holdIdCounter.getAndIncrement();
        String refNo = request.getOrDefault("referenceNo", "TX-MOCK-" + holdId).toString();
        String amount = request.getOrDefault("amount", "2500.00").toString();

        Map<String, Object> response = Map.of(
                "holdId", holdId,
                "referenceNo", refNo,
                "accountId", accountIdObj != null ? accountIdObj : 1,
                "amount", amount,
                "status", "RESERVED",
                "availableBalance", "47125.00",
                "ledgerBalance", "49625.00",
                "createdAt", Instant.now().toString()
        );

        log.info("[core-mock] Hold placed successfully: {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * L4: Capture Hold (POST /internal/v1/ledger/holds/{holdId}/capture)
     */
    @PostMapping("/holds/{holdId}/capture")
    public ResponseEntity<?> captureHold(@PathVariable Long holdId, @RequestBody(required = false) Map<String, Object> request) {
        log.info("[core-mock] Received capture hold request for holdId: {}, body: {}", holdId, request);

        long debitId = txnIdCounter.getAndIncrement();
        long creditId = txnIdCounter.getAndIncrement();

        Object dstAccount = (request != null && request.get("destinationAccountId") != null)
                ? request.get("destinationAccountId") : 2;

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("holdId", holdId);
        response.put("status", "POSTED");
        response.put("transferReference", "TX-20261005-" + holdId);
        response.put("debitTransactionId", debitId);
        response.put("creditTransactionId", creditId);
        response.put("sourceAccountId", 1);
        response.put("destinationAccountId", dstAccount);
        response.put("amount", "2500.00");
        response.put("sourceNewBalance", "47125.00");
        response.put("destinationNewBalance", "27875.00");
        response.put("auditHash", "a3f5b8e9c1d0f2a4e6b8c0d2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2");
        response.put("timestamp", Instant.now().toString());

        log.info("[core-mock] Hold captured: {}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * L5: Release Hold (POST /internal/v1/ledger/holds/{holdId}/release)
     */
    @PostMapping("/holds/{holdId}/release")
    public ResponseEntity<?> releaseHold(@PathVariable Long holdId, @RequestBody(required = false) Map<String, Object> request) {
        log.info("[core-mock] Received release hold request for holdId: {}, body: {}", holdId, request);

        Map<String, Object> response = Map.of(
                "holdId", holdId,
                "status", "CANCELLED",
                "availableBalance", "49625.00"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * L6: Get Hold (GET /internal/v1/ledger/holds/{holdId})
     */
    @GetMapping("/holds/{holdId}")
    public ResponseEntity<?> getHold(@PathVariable Long holdId) {
        Map<String, Object> response = Map.of(
                "holdId", holdId,
                "referenceNo", "TX-20261005-" + holdId,
                "accountId", 1,
                "amount", "2500.00",
                "status", "RESERVED",
                "availableBalance", "47125.00",
                "ledgerBalance", "49625.00",
                "createdAt", Instant.now().toString()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * L7: Compensating Reversal (POST /internal/v1/ledger/reversals)
     */
    @PostMapping("/reversals")
    public ResponseEntity<?> reverse(@RequestBody Map<String, Object> request) {
        log.info("[core-mock] Received reversal request: {}", request);

        long creditId = txnIdCounter.getAndIncrement();
        long debitId = txnIdCounter.getAndIncrement();

        String origRef = request.getOrDefault("originalReferenceNo", "TX-20261005-ORIG").toString();
        String revRef = request.getOrDefault("reversalReferenceNo", "RV-20261005-" + creditId).toString();

        Map<String, Object> response = Map.of(
                "reversalReferenceNo", revRef,
                "originalReferenceNo", origRef,
                "status", "POSTED",
                "entries", List.of(
                        Map.of(
                                "transactionId", creditId,
                                "accountId", 1,
                                "transactionType", "REVERSAL_CREDIT",
                                "amount", "2500.00",
                                "newBalance", "49625.00"
                        ),
                        Map.of(
                                "transactionId", debitId,
                                "accountId", 2,
                                "transactionType", "REVERSAL_DEBIT",
                                "amount", "2500.00",
                                "newBalance", "25375.00"
                        )
                ),
                "timestamp", Instant.now().toString()
        );

        log.info("[core-mock] Reversal posted successfully: {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * L1: Open balance (POST /internal/v1/ledger/balances)
     */
    @PostMapping("/balances")
    public ResponseEntity<?> openBalance(@RequestBody Map<String, Object> request) {
        Object accountId = request.getOrDefault("accountId", 1);
        String currency = request.getOrDefault("currency", "PHP").toString();

        Map<String, Object> response = Map.of(
                "accountId", accountId,
                "currency", currency,
                "availableBalance", "0.00",
                "ledgerBalance", "0.00",
                "status", "ACTIVE"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * L2: Get balance (GET /internal/v1/ledger/balances/{accountId})
     */
    @GetMapping("/balances/{accountId}")
    public ResponseEntity<?> getBalance(@PathVariable Long accountId) {
        Map<String, Object> response = Map.of(
                "accountId", accountId,
                "currency", "PHP",
                "availableBalance", "47125.00",
                "ledgerBalance", "49625.00",
                "status", "ACTIVE"
        );
        return ResponseEntity.ok(response);
    }
}
