package com.group2.fse.core_mock.controller;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MockInterbankPartnerControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private MockInterbankPartnerController controller;

    private static final String SHARED_SECRET = "coobs-interbank-secret-2026";

    @BeforeEach
    void setUp() {
        controller = new MockInterbankPartnerController();
        ReflectionTestUtils.setField(controller, "sharedSecret", SHARED_SECRET);
        ReflectionTestUtils.setField(controller, "authEnabled", true);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("H5: Should accept interbank transfer with valid API key")
    void testReceiveTransferSuccessApiKey() throws Exception {
        Map<String, Object> request = Map.of(
                "messageId", "0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21",
                "sourceBankCode", "COOBS",
                "destinationBankCode", "BANKB",
                "sender", Map.of("accountNumber", "ACC_10000001", "name", "John Doe"),
                "beneficiary", Map.of("accountNumber", "1000-2000-3000", "name", "Juan Dela Cruz"),
                "amount", "2500.00",
                "currency", "PHP",
                "senderReference", "TX-20261006-8F3A21C0",
                "createdAt", "2026-10-06T11:40:00Z"
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "COOBS")
                        .header("X-Api-Key", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.messageId").value("0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21"))
                .andExpect(jsonPath("$.beneficiaryName").value("J*** D*** C***"))
                .andExpect(jsonPath("$.receiverReference").exists())
                .andExpect(jsonPath("$.creditedAt").exists());
    }

    @Test
    @DisplayName("H5: Duplicate messageId returns cached response (Idempotency)")
    void testDuplicateMessageIdIdempotency() throws Exception {
        Map<String, Object> request = Map.of(
                "messageId", "idem-uuid-001",
                "sourceBankCode", "COOBS",
                "destinationBankCode", "BANKB",
                "sender", Map.of("accountNumber", "ACC_10000001", "name", "John Doe"),
                "beneficiary", Map.of("accountNumber", "1000-2000-3000", "name", "Juan Dela Cruz"),
                "amount", "1000.00",
                "currency", "PHP",
                "senderReference", "TX-IDEM-01",
                "createdAt", "2026-10-06T11:40:00Z"
        );

        // First call
        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "COOBS")
                        .header("X-Api-Key", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Second call with same messageId
        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "COOBS")
                        .header("X-Api-Key", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("H5: Unknown bank code should reject with 401 UNKNOWN_BANK")
    void testRejectUnknownBank() throws Exception {
        Map<String, Object> request = Map.of(
                "messageId", "uuid-unknown-bank",
                "sourceBankCode", "UNKNOWN",
                "amount", "500.00"
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "UNKNOWN")
                        .header("X-Api-Key", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNKNOWN_BANK"));
    }

    @Test
    @DisplayName("H5: Invalid API Key should reject with 401 INVALID_SIGNATURE")
    void testRejectInvalidApiKey() throws Exception {
        Map<String, Object> request = Map.of(
                "messageId", "uuid-wrong-secret",
                "sourceBankCode", "COOBS",
                "amount", "500.00"
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "COOBS")
                        .header("X-Api-Key", "wrong-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("INVALID_SIGNATURE"));
    }

    @Test
    @DisplayName("H5: Account starting with 999 triggers 422 ACCOUNT_NOT_FOUND")
    void testAccountNotFoundTrigger() throws Exception {
        Map<String, Object> request = Map.of(
                "messageId", "uuid-not-found-001",
                "sourceBankCode", "COOBS",
                "destinationBankCode", "BANKB",
                "beneficiary", Map.of("accountNumber", "9990123456", "name", "Invalid User"),
                "amount", "500.00",
                "currency", "PHP",
                "senderReference", "TX-FAIL-NF",
                "createdAt", "2026-10-06T11:40:00Z"
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "COOBS")
                        .header("X-Api-Key", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasonCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("H5: Account starting with 888 triggers 422 ACCOUNT_NOT_ACTIVE")
    void testAccountNotActiveTrigger() throws Exception {
        Map<String, Object> request = Map.of(
                "messageId", "uuid-dormant-001",
                "sourceBankCode", "COOBS",
                "destinationBankCode", "BANKB",
                "beneficiary", Map.of("accountNumber", "8880123456", "name", "Dormant User"),
                "amount", "500.00",
                "currency", "PHP",
                "senderReference", "TX-FAIL-NA",
                "createdAt", "2026-10-06T11:40:00Z"
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "COOBS")
                        .header("X-Api-Key", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reasonCode").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("H5: Status query returns 200 for processed message and 404 for unknown")
    void testTransferStatusQuery() throws Exception {
        // Unknown messageId
        mockMvc.perform(get("/api/v1/interbank/transfers/unknown-msg-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));

        // Post a transfer
        String msgId = "queryable-msg-123";
        Map<String, Object> request = Map.of(
                "messageId", msgId,
                "sourceBankCode", "COOBS",
                "destinationBankCode", "BANKB",
                "beneficiary", Map.of("accountNumber", "1234567890", "name", "Maria Santos"),
                "amount", "1500.00",
                "currency", "PHP",
                "senderReference", "TX-QUERY-01",
                "createdAt", "2026-10-06T11:40:00Z"
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "COOBS")
                        .header("X-Api-Key", SHARED_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Now query status
        mockMvc.perform(get("/api/v1/interbank/transfers/" + msgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(msgId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("H5: Account lookup returns masked name or 404")
    void testAccountLookup() throws Exception {
        // Active account
        mockMvc.perform(get("/api/v1/interbank/accounts/ACC_10000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.maskedName").value("J*** D*** C***"));

        // Non-existent account (starting with 999)
        mockMvc.perform(get("/api/v1/interbank/accounts/9991234567"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exists").value(false));
    }
}
