package com.group2.fse.transaction_service.interbank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.interbank.dto.InterbankAccountLookupResponse;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferRequest;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferResponse;
import com.group2.fse.transaction_service.interbank.security.InterbankAuthException;
import com.group2.fse.transaction_service.interbank.security.InterbankAuthValidator;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties;
import com.group2.fse.transaction_service.interbank.service.InterbankService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InterbankController: Inbound transfer webhooks and queries (FIN-407)")
class InterbankControllerTest {

    @Mock
    private InterbankService interbankService;

    @Mock
    private InterbankAuthValidator authValidator;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        InterbankController controller = new InterbankController(interbankService, authValidator, objectMapper, validator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private InterbankTransferRequest sampleRequest() {
        return InterbankTransferRequest.builder()
                .messageId("0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21")
                .sourceBankCode("BANKB")
                .destinationBankCode("COOBS")
                .sender(InterbankTransferRequest.Party.builder()
                        .accountNumber("1000-2000-3000")
                        .name("Jane Smith")
                        .build())
                .beneficiary(InterbankTransferRequest.Beneficiary.builder()
                        .accountNumber("ACC_10000004")
                        .name("Juan Dela Cruz")
                        .build())
                .amount("2500.00")
                .currency("PHP")
                .purpose("Payment for services")
                .senderReference("TX-20261003-8F3A21C0")
                .createdAt("2026-10-08T11:40:00Z")
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/interbank/transfers returns 200 OK when transfer accepted")
    void receiveTransferAccepted() throws Exception {
        InterbankTransferRequest request = sampleRequest();
        String json = objectMapper.writeValueAsString(request);

        when(interbankService.processInboundTransfer(any())).thenReturn(
                InterbankTransferResponse.accepted(request.getMessageId(), "IB-20261008-112233", "J*** D*** C***", Instant.now().toString())
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "BANKB")
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Api-Key", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.receiverReference").value("IB-20261008-112233"))
                .andExpect(jsonPath("$.beneficiaryName").value("J*** D*** C***"));
    }

    @Test
    @DisplayName("POST /api/v1/interbank/transfers/inbound (Jira alias) returns 200 OK")
    void receiveTransferInboundAliasAccepted() throws Exception {
        InterbankTransferRequest request = sampleRequest();
        String json = objectMapper.writeValueAsString(request);

        when(interbankService.processInboundTransfer(any())).thenReturn(
                InterbankTransferResponse.accepted(request.getMessageId(), "IB-20261008-112233", "J*** D*** C***", Instant.now().toString())
        );

        mockMvc.perform(post("/api/v1/interbank/transfers/inbound")
                        .header("X-Bank-Code", "BANKB")
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Api-Key", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST /api/v1/interbank/transfers/inward (AuraBank inward route) with X-Interbank-API-Key returns 200 OK")
    void receiveTransferInwardRouteAccepted() throws Exception {
        InterbankTransferRequest request = sampleRequest();
        request.setCreatedAt(null); // AuraBank spec does not provide createdAt
        String json = objectMapper.writeValueAsString(request);

        when(interbankService.processInboundTransfer(any())).thenReturn(
                InterbankTransferResponse.accepted(request.getMessageId(), "INW-0b6c2f6e", "A*** G***", Instant.now().toString())
        );

        mockMvc.perform(post("/api/v1/interbank/transfers/inward")
                        .header("X-Interbank-API-Key", "AURABANK-SECURE-KEY-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.receiverReference").value("INW-0b6c2f6e"));
    }

    @Test
    @DisplayName("POST /api/v1/interbank/transfers/inward with Authorization Bearer header returns 200 OK")
    void receiveTransferInwardBearerAccepted() throws Exception {
        InterbankTransferRequest request = sampleRequest();
        request.setCreatedAt(null);
        String json = objectMapper.writeValueAsString(request);

        when(interbankService.processInboundTransfer(any())).thenReturn(
                InterbankTransferResponse.accepted(request.getMessageId(), "INW-0b6c2f6e", "A*** G***", Instant.now().toString())
        );

        mockMvc.perform(post("/api/v1/interbank/transfers/inward")
                        .header("Authorization", "Bearer AURABANK-SECURE-KEY-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST /api/v1/interbank/transfers returns 401 when signature check fails")
    void authFailureReturns401() throws Exception {
        doThrow(new InterbankAuthException("INVALID_SIGNATURE", "Signature does not match."))
                .when(authValidator).validate(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "BANKB")
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reasonCode").value("INVALID_SIGNATURE"));
    }

    @Test
    @DisplayName("POST /api/v1/interbank/transfers returns 422 when rejected by service")
    void serviceRejectionReturns422() throws Exception {
        InterbankTransferRequest request = sampleRequest();
        String json = objectMapper.writeValueAsString(request);

        when(interbankService.processInboundTransfer(any())).thenReturn(
                InterbankTransferResponse.rejected(request.getMessageId(), "ACCOUNT_NOT_FOUND", "No account with that number.", Instant.now().toString())
        );

        mockMvc.perform(post("/api/v1/interbank/transfers")
                        .header("X-Bank-Code", "BANKB")
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Api-Key", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reasonCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/interbank/transfers/{messageId} returns 404 when not found")
    void getStatusNotFoundReturns404() throws Exception {
        when(interbankService.getTransferStatus("missing-id")).thenReturn(
                InterbankTransferResponse.notFound("missing-id")
        );

        mockMvc.perform(get("/api/v1/interbank/transfers/missing-id")
                        .header("X-Bank-Code", "BANKB")
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Api-Key", "test-secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/interbank/accounts/{accountNumber} returns 200 with masked name")
    void checkAccountReturns200() throws Exception {
        when(interbankService.checkAccount("ACC_10000004")).thenReturn(
                InterbankAccountLookupResponse.found("ACC_10000004", true, "C*** M***", "PHP")
        );

        mockMvc.perform(get("/api/v1/interbank/accounts/ACC_10000004")
                        .header("X-Bank-Code", "BANKB")
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Api-Key", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.maskedName").value("C*** M***"));
    }
}
