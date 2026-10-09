package com.group2.fse.transaction_service.interbank.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InterbankAuthValidator: Signature, API Key and Timestamp verification")
class InterbankAuthValidatorTest {

    private InterbankSecurityProperties properties;
    private InterbankAuthValidator validator;
    private static final String SECRET = "coobs-bankb-secret-2026";
    private static final String BANK_CODE = "BANKB";

    @BeforeEach
    void setUp() {
        properties = new InterbankSecurityProperties();
        properties.setDefaultSharedSecret(SECRET);
        InterbankSecurityProperties.PartnerConfig partner = new InterbankSecurityProperties.PartnerConfig();
        partner.setBankCode(BANK_CODE);
        partner.setSharedSecret(SECRET);
        partner.setSettlementAccountId(9005L);
        properties.getPartners().put(BANK_CODE, partner);

        validator = new InterbankAuthValidator(properties);
    }

    @Test
    @DisplayName("Valid HMAC-SHA256 signature passes validation")
    void validSignaturePasses() {
        String timestamp = Instant.now().toString();
        String rawBody = "{\"messageId\":\"123\",\"amount\":\"2500.00\"}";
        String toSign = timestamp + "." + rawBody;
        String signature = InterbankAuthValidator.computeHmacSha256Hex(SECRET, toSign);

        validator.validate(BANK_CODE, timestamp, signature, null, rawBody);
    }

    @Test
    @DisplayName("Valid X-Api-Key passes validation without signature")
    void validApiKeyPasses() {
        String timestamp = Instant.now().toString();
        String rawBody = "{\"messageId\":\"123\"}";

        validator.validate(BANK_CODE, timestamp, null, SECRET, rawBody);
    }

    @Test
    @DisplayName("Missing X-Bank-Code throws UNKNOWN_BANK")
    void missingBankCodeThrows() {
        String timestamp = Instant.now().toString();
        assertThatThrownBy(() -> validator.validate(null, timestamp, "sig", null, "{}"))
                .isInstanceOf(InterbankAuthException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "UNKNOWN_BANK");
    }

    @Test
    @DisplayName("Missing X-Timestamp throws INVALID_REQUEST")
    void missingTimestampThrows() {
        assertThatThrownBy(() -> validator.validate(BANK_CODE, null, "sig", null, "{}"))
                .isInstanceOf(InterbankAuthException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "INVALID_REQUEST");
    }

    @Test
    @DisplayName("Timestamp older than 5 minutes throws TIMESTAMP_EXPIRED")
    void expiredTimestampThrows() {
        String oldTimestamp = Instant.now().minus(6, ChronoUnit.MINUTES).toString();
        assertThatThrownBy(() -> validator.validate(BANK_CODE, oldTimestamp, "sig", null, "{}"))
                .isInstanceOf(InterbankAuthException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "TIMESTAMP_EXPIRED");
    }

    @Test
    @DisplayName("Invalid signature throws INVALID_SIGNATURE")
    void invalidSignatureThrows() {
        String timestamp = Instant.now().toString();
        String rawBody = "{}";
        assertThatThrownBy(() -> validator.validate(BANK_CODE, timestamp, "wronghexsignature12345", null, rawBody))
                .isInstanceOf(InterbankAuthException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Missing both signature and API key throws INVALID_SIGNATURE")
    void missingAuthHeadersThrows() {
        String timestamp = Instant.now().toString();
        assertThatThrownBy(() -> validator.validate(BANK_CODE, timestamp, null, null, "{}"))
                .isInstanceOf(InterbankAuthException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Unknown partner bank code throws UNKNOWN_BANK")
    void unknownBankCodeThrows() {
        String timestamp = Instant.now().toString();
        assertThatThrownBy(() -> validator.validate("UNKNOWN_BANK", timestamp, "sig", null, "{}"))
                .isInstanceOf(InterbankAuthException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "UNKNOWN_BANK");
    }

    @Test
    @DisplayName("Partner with authRequired=false passes without signature, apiKey, or timestamp (e.g. AURABANK)")
    void authNotRequiredPassesWithoutAuthHeaders() {
        InterbankSecurityProperties.PartnerConfig noAuthPartner = new InterbankSecurityProperties.PartnerConfig();
        noAuthPartner.setBankCode("AURABANK");
        noAuthPartner.setAuthRequired(false);
        noAuthPartner.setSettlementAccountId(9005L);
        properties.getPartners().put("AURABANK", noAuthPartner);

        // Should pass with null timestamp, null signature, null apiKey
        validator.validate("AURABANK", null, null, null, "{\"messageId\":\"aura-1\"}");
    }
}

