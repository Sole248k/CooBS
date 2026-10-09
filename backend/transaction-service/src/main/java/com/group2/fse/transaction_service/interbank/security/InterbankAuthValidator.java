package com.group2.fse.transaction_service.interbank.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

/**
 * Validates HMAC-SHA256 signatures and API keys for inbound interbank API calls
 * as defined in Section 4 of INTERBANK_TRANSFER_PROPOSAL.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterbankAuthValidator {

    private final InterbankSecurityProperties properties;

    /**
     * Validates headers and body for inbound interbank calls.
     *
     * @param bankCode Header X-Bank-Code
     * @param timestamp Header X-Timestamp (ISO-8601 UTC)
     * @param signature Header X-Signature (Hex of HMAC-SHA256 over timestamp.rawBody)
     * @param apiKey Header X-Api-Key (alternative simpler profile)
     * @param rawBody The raw UTF-8 request body string (empty string for GET)
     */
    public void validate(String bankCode, String timestamp, String signature, String apiKey, String rawBody) {
        if (bankCode == null || bankCode.isBlank()) {
            throw new InterbankAuthException("UNKNOWN_BANK", "X-Bank-Code header is required.");
        }

        InterbankSecurityProperties.PartnerConfig partner = properties.getPartner(bankCode);
        if (partner == null) {
            log.warn("Unknown bank code in interbank request: {}", bankCode);
            throw new InterbankAuthException("UNKNOWN_BANK", "X-Bank-Code not recognised.");
        }

        // If partner is configured with authRequired=false, bypass signature, API key and timestamp checks
        if (!partner.isAuthRequired()) {
            log.info("Interbank authentication bypassed for partner {} (authRequired=false)", bankCode);
            return;
        }

        if (partner.getSharedSecret() == null || partner.getSharedSecret().isBlank()) {
            log.warn("Partner bank {} has no shared secret configured", bankCode);
            throw new InterbankAuthException("UNKNOWN_BANK", "X-Bank-Code not recognised.");
        }

        if (timestamp == null || timestamp.isBlank()) {
            throw new InterbankAuthException("INVALID_REQUEST", "X-Timestamp header is required.");
        }

        // Validate timestamp clock skew
        try {
            Instant requestTime = Instant.parse(timestamp);
            Duration skew = Duration.between(requestTime, Instant.now()).abs();
            if (skew.toSeconds() > properties.getAllowedTimestampSkewSeconds()) {
                log.warn("Interbank request rejected due to expired timestamp: timestamp={}, skewSeconds={}",
                        timestamp, skew.toSeconds());
                throw new InterbankAuthException("TIMESTAMP_EXPIRED", "Timestamp outside the five-minute window.");
            }
        } catch (DateTimeParseException ex) {
            log.warn("Invalid ISO-8601 timestamp in interbank request: {}", timestamp);
            throw new InterbankAuthException("INVALID_REQUEST", "Malformed X-Timestamp header.");
        }

        String sharedSecret = partner.getSharedSecret();

        // 1. Signature profile (preferred)
        if (signature != null && !signature.isBlank()) {
            String payloadToSign = timestamp + "." + (rawBody != null ? rawBody : "");
            String expectedSignature = computeHmacSha256Hex(sharedSecret, payloadToSign);

            if (!MessageDigest.isEqual(
                    expectedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    signature.toLowerCase().trim().getBytes(StandardCharsets.UTF_8))) {
                log.warn("Interbank signature mismatch for bank {}: expected={}, received={}",
                        bankCode, expectedSignature, signature);
                throw new InterbankAuthException("INVALID_SIGNATURE", "Signature does not match.");
            }
            return;
        }

        // 2. API Key profile (simpler alternative)
        if (apiKey != null && !apiKey.isBlank()) {
            if (!MessageDigest.isEqual(
                    sharedSecret.getBytes(StandardCharsets.UTF_8),
                    apiKey.trim().getBytes(StandardCharsets.UTF_8))) {
                log.warn("Interbank API key mismatch for bank {}", bankCode);
                throw new InterbankAuthException("INVALID_SIGNATURE", "API key does not match.");
            }
            return;
        }

        log.warn("Interbank request missing both X-Signature and X-Api-Key headers from bank {}", bankCode);
        throw new InterbankAuthException("INVALID_SIGNATURE", "Missing X-Signature or X-Api-Key header.");
    }

    public static String computeHmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", ex);
        }
    }
}
