package com.group2.fse.risk_engine.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.risk_engine.dto.FraudCheckResult;
import com.group2.fse.risk_engine.dto.LimitCheckResult;
import com.group2.fse.risk_engine.dto.RiskEvaluationRequest;
import com.group2.fse.risk_engine.dto.RiskEvaluationResponse;
import com.group2.fse.risk_engine.security.jwt.JwtTokenProvider;
import com.group2.fse.risk_engine.service.RiskEvaluator;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RiskControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RiskEvaluator riskEvaluator;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Should evaluate risk and return 200 OK with frozen contract structure when authorized with ROLE_SERVICE")
    void testEvaluateRiskSuccess() throws Exception {
        // Given: Valid service token
        String serviceToken = jwtTokenProvider.generateToken(0L, "orchestration-service", List.of("ROLE_SERVICE"));

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .customerId(1L)
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_3_FULL")
                .dailyLimit(new BigDecimal("100000.00"))
                .isNewBeneficiary(false)
                .build();

        RiskEvaluationResponse mockResponse = RiskEvaluationResponse.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .decision("APPROVE")
                .score("0.18")
                .fraudCheck(FraudCheckResult.builder().outcome("PASSED").reasons(List.of()).build())
                .limitCheck(LimitCheckResult.builder().outcome("PASSED").usedToday("5000.00").dailyLimit("100000.00").build())
                .evaluatedInMs(12)
                .build();

        when(riskEvaluator.evaluate(any())).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/internal/v1/risk/evaluate")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.score").value("0.18"))
                .andExpect(jsonPath("$.fraudCheck.outcome").value("PASSED"))
                .andExpect(jsonPath("$.limitCheck.outcome").value("PASSED"))
                .andExpect(jsonPath("$.limitCheck.usedToday").value("5000.00"))
                .andExpect(jsonPath("$.limitCheck.dailyLimit").value("100000.00"))
                .andExpect(jsonPath("$.evaluatedInMs").value(12));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when Authorization header is missing")
    void testEvaluateRiskUnauthorized() throws Exception {
        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .customerId(1L)
                .amount(new BigDecimal("2500.00"))
                .build();

        mockMvc.perform(post("/internal/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 403 Forbidden when caller does not possess ROLE_SERVICE")
    void testEvaluateRiskForbiddenForCustomerRole() throws Exception {
        // Given: Customer token (not a service token)
        String customerToken = jwtTokenProvider.generateToken(1L, "john_doe", List.of("ROLE_CUSTOMER"));

        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .customerId(1L)
                .amount(new BigDecimal("2500.00"))
                .build();

        mockMvc.perform(post("/internal/v1/risk/evaluate")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should evaluate risk and return 200 OK when authenticated via X-Internal-Secret header (FIN-405)")
    void testEvaluateRiskSuccessWithXInternalSecret() throws Exception {
        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .customerId(1L)
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .channel("APP")
                .kycTier("TIER_3_FULL")
                .dailyLimit(new BigDecimal("100000.00"))
                .isNewBeneficiary(false)
                .transfersInLast10Min(0)
                .build();

        RiskEvaluationResponse mockResponse = RiskEvaluationResponse.builder()
                .evaluationId("eval-test-secret-01")
                .transactionId(request.getTransactionId())
                .decision("APPROVE")
                .score("0.10")
                .riskScore(0.10)
                .fraudCheck(FraudCheckResult.builder().outcome("PASSED").reasons(List.of()).build())
                .limitCheck(LimitCheckResult.builder().outcome("PASSED").usedToday("0.00").dailyLimit("100000.00").build())
                .reasons(List.of())
                .evaluatedAt(java.time.Instant.now())
                .evaluatedInMs(12L)
                .executionDurationMs(12L)
                .build();

        when(riskEvaluator.evaluate(any(RiskEvaluationRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/internal/v1/risk/evaluate")
                        .header("X-Internal-Secret", "test-internal-risk-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.score").value("0.10"));
    }

    @Test
    @DisplayName("Should evaluate risk and return 200 OK when authenticated via X-API-Key header (FIN-405)")
    void testEvaluateRiskSuccessWithXApiKey() throws Exception {
        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .customerId(1L)
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .build();

        RiskEvaluationResponse mockResponse = RiskEvaluationResponse.builder()
                .evaluationId("eval-test-key-01")
                .transactionId(request.getTransactionId())
                .decision("APPROVE")
                .score("0.15")
                .riskScore(0.15)
                .fraudCheck(FraudCheckResult.builder().outcome("PASSED").reasons(List.of()).build())
                .limitCheck(LimitCheckResult.builder().outcome("PASSED").usedToday("0.00").dailyLimit("100000.00").build())
                .reasons(List.of())
                .evaluatedAt(java.time.Instant.now())
                .evaluatedInMs(5L)
                .executionDurationMs(5L)
                .build();

        when(riskEvaluator.evaluate(any(RiskEvaluationRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/internal/v1/risk/evaluate")
                        .header("X-API-Key", "test-internal-risk-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"));
    }

    @Test
    @DisplayName("R2: a failed outcome from a service is recorded and answered with what was released")
    void testRecordOutcomeSuccess() throws Exception {
        String serviceToken = jwtTokenProvider.generateToken(0L, "orchestration-service", List.of("ROLE_SERVICE"));
        when(riskEvaluator.recordOutcome(any(com.group2.fse.risk_engine.dto.RiskOutcomeRequest.class)))
                .thenReturn(com.group2.fse.risk_engine.dto.RiskOutcomeResponse.builder()
                        .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11").outcome("FAILED")
                        .released(true).releasedAmount("2500.00").build());

        mockMvc.perform(post("/internal/v1/risk/outcomes")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11\",\"outcome\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"))
                .andExpect(jsonPath("$.outcome").value("FAILED"))
                .andExpect(jsonPath("$.released").value(true))
                .andExpect(jsonPath("$.releasedAmount").value("2500.00"));
    }

    @Test
    @DisplayName("R2: a reversed outcome names the customer and is answered with today's reversal count")
    void testRecordReversedOutcome() throws Exception {
        String serviceToken = jwtTokenProvider.generateToken(0L, "orchestration-service", List.of("ROLE_SERVICE"));
        when(riskEvaluator.recordOutcome(any(com.group2.fse.risk_engine.dto.RiskOutcomeRequest.class)))
                .thenReturn(com.group2.fse.risk_engine.dto.RiskOutcomeResponse.builder()
                        .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11").outcome("REVERSED")
                        .released(false).reversalsToday(1).build());

        mockMvc.perform(post("/internal/v1/risk/outcomes")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11\",\"outcome\":\"REVERSED\",\"customerId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REVERSED"))
                .andExpect(jsonPath("$.released").value(false))
                .andExpect(jsonPath("$.reversalsToday").value(1))
                .andExpect(jsonPath("$.releasedAmount").doesNotExist());

        // Without the customer there is nobody to count it for.
        mockMvc.perform(post("/internal/v1/risk/outcomes")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11\",\"outcome\":\"REVERSED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R2: an outcome other than FAILED or REVERSED, or a call without a service token, is refused")
    void testRecordOutcomeRefusals() throws Exception {
        String serviceToken = jwtTokenProvider.generateToken(0L, "orchestration-service", List.of("ROLE_SERVICE"));

        mockMvc.perform(post("/internal/v1/risk/outcomes")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11\",\"outcome\":\"POSTED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/internal/v1/risk/outcomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11\",\"outcome\":\"FAILED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when invalid API secret is provided (FIN-405)")
    void testEvaluateRiskUnauthorizedWithInvalidSecret() throws Exception {
        RiskEvaluationRequest request = RiskEvaluationRequest.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .customerId(1L)
                .amount(new BigDecimal("2500.00"))
                .build();

        mockMvc.perform(post("/internal/v1/risk/evaluate")
                        .header("X-Internal-Secret", "wrong-invalid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_CREDENTIALS"));
    }
}
