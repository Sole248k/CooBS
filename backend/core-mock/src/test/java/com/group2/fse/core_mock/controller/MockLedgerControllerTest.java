package com.group2.fse.core_mock.controller;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MockLedgerControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MockLedgerController()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("L3: Should place hold successfully for normal account")
    void testPlaceHoldSuccess() throws Exception {
        Map<String, Object> request = Map.of(
                "transactionId", "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
                "referenceNo", "TX-20261005-8F3A21C0",
                "accountId", 1,
                "amount", "2500.00",
                "currency", "PHP",
                "actorId", 1
        );

        mockMvc.perform(post("/internal/v1/ledger/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.amount").value("2500.00"));
    }

    @Test
    @DisplayName("L3: Should return 422 INSUFFICIENT_FUNDS when account starts with 0999")
    void testPlaceHoldInsufficientFundsTrigger() throws Exception {
        Map<String, Object> request = Map.of(
                "transactionId", "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
                "referenceNo", "TX-20261005-FAIL",
                "accountId", "09991234",
                "amount", "50000.00"
        );

        mockMvc.perform(post("/internal/v1/ledger/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("L4 & L5: Should capture and release hold")
    void testCaptureAndReleaseHold() throws Exception {
        // Capture
        mockMvc.perform(post("/internal/v1/ledger/holds/9001/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationAccountId\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        // Release
        mockMvc.perform(post("/internal/v1/ledger/holds/9001/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"TEST_RELEASE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("L7: Should post balanced reversal entries")
    void testReversalPost() throws Exception {
        Map<String, Object> request = Map.of(
                "originalReferenceNo", "TX-20261005-8F3A21C0",
                "reversalReferenceNo", "RV-20261005-11AA22BB",
                "reason", "Customer dispute approved",
                "actorId", 3
        );

        mockMvc.perform(post("/internal/v1/ledger/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.entries[0].transactionType").value("REVERSAL_CREDIT"))
                .andExpect(jsonPath("$.entries[1].transactionType").value("REVERSAL_DEBIT"));
    }
}
