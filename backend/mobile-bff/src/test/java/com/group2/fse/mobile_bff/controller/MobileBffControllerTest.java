package com.group2.fse.mobile_bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.mobile_bff.dto.*;
import com.group2.fse.mobile_bff.security.UserPrincipal;
import com.group2.fse.mobile_bff.service.MobileBffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MobileBffControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MobileBffService mobileBffService;

    @InjectMocks
    private MobileBffController mobileBffController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(mobileBffController)
                .setControllerAdvice(new com.group2.fse.mobile_bff.exception.GlobalExceptionHandler())
                .build();

        UserPrincipal principal = UserPrincipal.builder()
                .userId(1L)
                .username("john_doe")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, "mock-token", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testGetDashboard_Returns200() throws Exception {
        MobileDashboardResponse response = MobileDashboardResponse.builder()
                .member(MobileMemberSummary.builder()
                        .customerId(1L)
                        .displayName("John D.")
                        .email("john.doe@example.com")
                        .kycStatus("VERIFIED")
                        .build())
                .primaryAccount(MobileAccountSummary.builder()
                        .accountId(1L)
                        .accountNumber("ACC_10000001")
                        .accountType("SAVINGS")
                        .currency("PHP")
                        .availableBalance("50000.00")
                        .formattedBalance("₱50,000.00")
                        .status("ACTIVE")
                        .build())
                .unreadNotifications(2)
                .recentTransactions(List.of(
                        MobileRecentTransaction.builder()
                                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                                .referenceNo("TX-20261003-8F3A21C0")
                                .trackingReference("TX-20261003-8F3A21C0")
                                .amount("2500.00")
                                .formattedAmount("-₱2,500.00")
                                .currency("PHP")
                                .status("POSTED")
                                .remarks("Lunch money")
                                .submittedAt("2026-10-03T11:40:00Z")
                                .build()
                ))
                .unavailable(List.of())
                .asOf("2026-10-03T11:45:00Z")
                .build();

        when(mobileBffService.getDashboard(anyLong(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/mobile/dashboard")
                        .header("Authorization", "Bearer mock-token")
                        .header("X-Correlation-Id", "corr-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.displayName").value("John D."))
                .andExpect(jsonPath("$.primaryAccount.accountNumber").value("ACC_10000001"))
                .andExpect(jsonPath("$.unreadNotifications").value(2))
                .andExpect(jsonPath("$.recentTransactions[0].trackingReference").value("TX-20261003-8F3A21C0"));
    }

    @Test
    void testGetTimeline_Returns200() throws Exception {
        MobileTimelineResponse response = MobileTimelineResponse.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .referenceNo("TX-20261003-8F3A21C0")
                .trackingReference("TX-20261003-8F3A21C0")
                .status("PROCESSING")
                .overallStatus("PROCESSING")
                .view("CUSTOMER")
                .currentStage(1)
                .totalStages(3)
                .stages(List.of(
                        MobileTimelineStage.builder()
                                .stageNumber(1)
                                .stageKey("SUBMITTED")
                                .stageName("INITIATED")
                                .title("Transfer submitted")
                                .subtitle("We received your transfer request.")
                                .status("COMPLETED")
                                .outcome("PASSED")
                                .occurredAt("2026-10-03T11:40:00.010Z")
                                .build()
                ))
                .build();

        when(mobileBffService.getTimeline(anyString(), anyLong(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/mobile/transactions/TX-20261003-8F3A21C0/timeline")
                        .header("Authorization", "Bearer mock-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingReference").value("TX-20261003-8F3A21C0"))
                .andExpect(jsonPath("$.view").value("CUSTOMER"))
                .andExpect(jsonPath("$.stages[0].stageKey").value("SUBMITTED"));
    }

    @Test
    void testSubmitRemittance_Returns202() throws Exception {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .category("TRANSFER")
                .remarks("Lunch money")
                .build();

        MobileRemittanceResponse response = MobileRemittanceResponse.builder()
                .transactionId("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .referenceNo("TX-20261003-8F3A21C0")
                .status("INITIATED")
                .amount("2500.00")
                .currency("PHP")
                .createdAt("2026-10-03T11:40:00Z")
                .timelineUrl("/api/v1/mobile/transactions/TX-20261003-8F3A21C0/timeline")
                .build();

        when(mobileBffService.submitRemittance(any(), any(), anyLong(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/mobile/transactions/remittance")
                        .header("Authorization", "Bearer mock-token")
                        .header("Idempotency-Key", "IDEMP-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.referenceNo").value("TX-20261003-8F3A21C0"))
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.timelineUrl").value("/api/v1/mobile/transactions/TX-20261003-8F3A21C0/timeline"));
    }

    @Test
    void testSubmitRemittance_MissingIdempotencyKey_Returns400() throws Exception {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("100.00"))
                .build();

        mockMvc.perform(post("/api/v1/mobile/transactions/remittance")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing mandatory Idempotency-Key header"));
    }

    @Test
    void testGetDashboard_Unauthenticated_Returns401() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/v1/mobile/dashboard")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
