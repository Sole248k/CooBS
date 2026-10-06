package com.group2.fse.core_mock.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * Handshake H5 / H16 Interbank Partner Mock Controller.
 * Implements the interbank partner contract defined in contracts/api/INTERBANK_TRANSFER_PROPOSAL.md.
 * Allows orchestration-service (Carl) and interbank adapters to simulate credit transfers, status inquiries,
 * and account verification with partner banks.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/interbank")
public class MockInterbankPartnerController {

    private final AtomicLong refCounter = new AtomicLong(10001);
    private final Map<String, ResponseEntity<?>> transferStore = new ConcurrentHashMap<>();

    @Value("${app.interbank.shared-secret:coobs-interbank-secret-2026}")
    private String sharedSecret;

    @Value("${app.interbank.auth-enabled:true}")
    private boolean authEnabled;

    /**
     * Endpoint 1: Receive a transfer credit (POST /api/v1/interbank/transfers)
     */
    @PostMapping(value = "/transfers", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> receiveTransfer(
            @RequestHeader(value = "X-Bank-Code", required = false) String bankCodeHeader,
            @RequestHeader(value = "X-Timestamp", required = false) String timestampHeader,
            @RequestHeader(value = "X-Signature", required = false) String signatureHeader,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader,
            @RequestBody Map<String, Object> request) {

        log.info("[interbank-mock] Received inward transfer request. Bank: {}, Message: {}", 
                bankCodeHeader, request.get("messageId"));

        // 1. Security Header Validation (if auth enabled)
        if (authEnabled) {
            ResponseEntity<?> authError = validateAuth(bankCodeHeader, timestampHeader, signatureHeader, apiKeyHeader, request);
            if (authError != null) {
                return authError;
            }
        }

        // 2. Validate Required Payload Fields
        String messageId = (String) request.get("messageId");
        if (messageId == null || messageId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "REJECTED",
                    "reasonCode", "INVALID_REQUEST",
                    "reasonMessage", "Field 'messageId' is required."
            ));
        }

        // 3. Idempotency Check: repeated messageId returns original response
        if (transferStore.containsKey(messageId)) {
            log.info("[interbank-mock] Duplicate messageId [{}] detected. Returning cached response.", messageId);
            return transferStore.get(messageId);
        }

        String sourceBankCode = (String) request.get("sourceBankCode");
        String destinationBankCode = (String) request.get("destinationBankCode");
        String amountStr = (String) request.get("amount");
        String currency = (String) request.get("currency");

        @SuppressWarnings("unchecked")
        Map<String, Object> beneficiary = (Map<String, Object>) request.get("beneficiary");
        String beneficiaryAcc = beneficiary != null ? (String) beneficiary.get("accountNumber") : null;
        String beneficiaryName = beneficiary != null ? (String) beneficiary.get("name") : "Juan Dela Cruz";

        // Validate currency
        if (currency != null && !currency.equalsIgnoreCase("PHP")) {
            Map<String, Object> error = Map.of(
                    "messageId", messageId,
                    "status", "REJECTED",
                    "reasonCode", "CURRENCY_NOT_SUPPORTED",
                    "reasonMessage", "Currency must be PHP.",
                    "rejectedAt", Instant.now().toString()
            );
            ResponseEntity<?> response = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
            transferStore.put(messageId, response);
            return response;
        }

        // Test Trigger 1: Account starting with 999 or amount 999.00 -> ACCOUNT_NOT_FOUND (422)
        if ((beneficiaryAcc != null && (beneficiaryAcc.startsWith("999") || beneficiaryAcc.startsWith("0999")))
                || "999.00".equals(amountStr)) {
            log.warn("[interbank-mock] Triggering ACCOUNT_NOT_FOUND for account: {}", beneficiaryAcc);
            Map<String, Object> error = Map.of(
                    "messageId", messageId,
                    "status", "REJECTED",
                    "reasonCode", "ACCOUNT_NOT_FOUND",
                    "reasonMessage", "No account with that number.",
                    "rejectedAt", Instant.now().toString()
            );
            ResponseEntity<?> response = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
            transferStore.put(messageId, response);
            return response;
        }

        // Test Trigger 2: Account starting with 888 -> ACCOUNT_NOT_ACTIVE (422)
        if (beneficiaryAcc != null && (beneficiaryAcc.startsWith("888") || beneficiaryAcc.startsWith("0888"))) {
            log.warn("[interbank-mock] Triggering ACCOUNT_NOT_ACTIVE for account: {}", beneficiaryAcc);
            Map<String, Object> error = Map.of(
                    "messageId", messageId,
                    "status", "REJECTED",
                    "reasonCode", "ACCOUNT_NOT_ACTIVE",
                    "reasonMessage", "Account is dormant or frozen.",
                    "rejectedAt", Instant.now().toString()
            );
            ResponseEntity<?> response = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
            transferStore.put(messageId, response);
            return response;
        }

        // Test Trigger 3: Account starting with 777 or amount 777.00 -> Timeout delay (5000ms)
        if ((beneficiaryAcc != null && (beneficiaryAcc.startsWith("777") || beneficiaryAcc.startsWith("0777")))
                || "777.00".equals(amountStr)) {
            log.warn("[interbank-mock] Triggering 5000ms timeout simulation for account: {}", beneficiaryAcc);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }

        // 4. Normal Success (HTTP 200 ACCEPTED)
        String receiverRef = "IB-" + DateTimeFormatterUtil.compactDate() + "-" + refCounter.getAndIncrement();
        String maskedBeneficiary = maskName(beneficiaryName != null ? beneficiaryName : "Juan Dela Cruz");

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("messageId", messageId);
        responseBody.put("status", "ACCEPTED");
        responseBody.put("receiverReference", receiverRef);
        responseBody.put("beneficiaryName", maskedBeneficiary);
        responseBody.put("creditedAt", Instant.now().toString());

        ResponseEntity<?> response = ResponseEntity.ok(responseBody);
        transferStore.put(messageId, response);
        log.info("[interbank-mock] Transfer accepted successfully: {}", responseBody);
        return response;
    }

    /**
     * Endpoint 2: Transfer status inquiry (GET /api/v1/interbank/transfers/{messageId})
     */
    @GetMapping(value = "/transfers/{messageId}", produces = "application/json")
    public ResponseEntity<?> getTransferStatus(@PathVariable String messageId) {
        log.info("[interbank-mock] Status inquiry for messageId: {}", messageId);
        if (transferStore.containsKey(messageId)) {
            return transferStore.get(messageId);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "messageId", messageId,
                "status", "NOT_FOUND"
        ));
    }

    /**
     * Endpoint 3: Account verification / name lookup (GET /api/v1/interbank/accounts/{accountNumber})
     */
    @GetMapping(value = "/accounts/{accountNumber}", produces = "application/json")
    public ResponseEntity<?> checkAccount(@PathVariable String accountNumber) {
        log.info("[interbank-mock] Account check for: {}", accountNumber);

        if (accountNumber == null || accountNumber.isBlank() || accountNumber.startsWith("999") || accountNumber.startsWith("0999")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "accountNumber", accountNumber != null ? accountNumber : "",
                    "exists", false
            ));
        }

        boolean isActive = !(accountNumber.startsWith("888") || accountNumber.startsWith("0888"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountNumber", accountNumber);
        result.put("exists", true);
        result.put("active", isActive);
        result.put("maskedName", "J*** D*** C***");
        result.put("currency", "PHP");

        return ResponseEntity.ok(result);
    }

    private ResponseEntity<?> validateAuth(
            String bankCode, String timestamp, String signature, String apiKey, Map<String, Object> body) {

        // Validate bank code
        if (bankCode == null || bankCode.isBlank() || "UNKNOWN".equalsIgnoreCase(bankCode)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "REJECTED",
                    "reasonCode", "UNKNOWN_BANK",
                    "reasonMessage", "X-Bank-Code is missing or unrecognized."
            ));
        }

        // Option A: API Key profile
        if (apiKey != null && !apiKey.isBlank()) {
            if (!apiKey.equals(sharedSecret)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "status", "REJECTED",
                        "reasonCode", "INVALID_SIGNATURE",
                        "reasonMessage", "X-Api-Key does not match shared secret."
                ));
            }
            return null; // Valid API Key
        }

        // Option B: Signed HMAC profile
        if (signature != null && !signature.isBlank()) {
            if (timestamp == null || timestamp.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "status", "REJECTED",
                        "reasonCode", "TIMESTAMP_EXPIRED",
                        "reasonMessage", "X-Timestamp header is required for signed requests."
                ));
            }

            // Check timestamp skew (within 5 minutes)
            try {
                Instant reqTime = Instant.parse(timestamp);
                if (Duration.between(reqTime, Instant.now()).abs().toMinutes() > 5) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "status", "REJECTED",
                            "reasonCode", "TIMESTAMP_EXPIRED",
                            "reasonMessage", "X-Timestamp is outside the allowed 5-minute window."
                    ));
                }
            } catch (DateTimeParseException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "status", "REJECTED",
                        "reasonCode", "TIMESTAMP_EXPIRED",
                        "reasonMessage", "X-Timestamp must be ISO-8601 UTC."
                ));
            }

            // Verify HMAC signature
            // If body is empty, signed text is timestamp + "."
            // For mock, verify either against raw body or allow secret-matched signature
            try {
                Mac hmac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKey = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                hmac.init(secretKey);

                // If signature matches directly or matches calculated HMAC
                byte[] expectedBytes = hmac.doFinal(timestamp.getBytes(StandardCharsets.UTF_8));
                String expectedHex = HexFormat.of().formatHex(expectedBytes);

                if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expectedHex.getBytes(StandardCharsets.UTF_8))
                        && !signature.equals(sharedSecret)) {
                    log.warn("[interbank-mock] Invalid signature. Provided: {}", signature);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "status", "REJECTED",
                            "reasonCode", "INVALID_SIGNATURE",
                            "reasonMessage", "HMAC signature does not match."
                    ));
                }
            } catch (Exception e) {
                log.error("[interbank-mock] Error verifying HMAC", e);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "status", "REJECTED",
                        "reasonCode", "INVALID_SIGNATURE",
                        "reasonMessage", "Failed to compute HMAC."
                ));
            }

            return null; // Valid Signature
        }

        // Neither API key nor signature provided
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", "REJECTED",
                "reasonCode", "INVALID_SIGNATURE",
                "reasonMessage", "Either X-Api-Key or X-Signature must be provided."
        ));
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "J*** D*** C***";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append("***");
            }
            if (i < parts.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private static class DateTimeFormatterUtil {
        public static String compactDate() {
            return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        }
    }
}
