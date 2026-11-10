package com.group2.fse.account_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.account_service.dto.sharecapital.*;
import com.group2.fse.account_service.exception.GlobalExceptionHandler;
import com.group2.fse.account_service.security.jwt.UserPrincipal;
import com.group2.fse.account_service.service.ShareCapitalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Share Capital Controller REST API Contract Tests")
class ShareCapitalControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ShareCapitalService shareCapitalService;

    @InjectMocks
    private ShareCapitalController shareCapitalController;

    private UserPrincipal customerPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        customerPrincipal = UserPrincipal.create(1001L, "maria_santos", List.of("ROLE_CUSTOMER"));

        mockMvc = MockMvcBuilders.standaloneSetup(shareCapitalController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter,
                                                  ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest,
                                                  WebDataBinderFactory binderFactory) {
                        return customerPrincipal;
                    }
                })
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/accounts/share-capital: returns 200 with member certificate and equity metrics")
    void testGetMyShareCapital() throws Exception {
        ShareCapitalResponse mockRes = ShareCapitalResponse.builder()
                .shareCapitalId(100L)
                .certificateNo("SC-2024-001001")
                .customerId(1001L)
                .accountId(10L)
                .accountNumber("ACC_10000001")
                .subscribedShares(500)
                .paidUpShares(250)
                .parValue(new BigDecimal("100.0000"))
                .totalPaidAmount(new BigDecimal("25000.0000"))
                .subscribedAmount(new BigDecimal("50000.0000"))
                .minimumRequiredShares(20)
                .dividendRate(new BigDecimal("7.25"))
                .membershipType("REGULAR")
                .hasVotingRights(true)
                .completionRatio(0.5)
                .status("ACTIVE")
                .build();

        when(shareCapitalService.getMemberShareCapital(1001L)).thenReturn(mockRes);

        mockMvc.perform(get("/api/v1/accounts/share-capital")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateNo").value("SC-2024-001001"))
                .andExpect(jsonPath("$.subscribedShares").value(500))
                .andExpect(jsonPath("$.paidUpShares").value(250))
                .andExpect(jsonPath("$.totalPaidAmount").value(25000.0))
                .andExpect(jsonPath("$.membershipType").value("REGULAR"))
                .andExpect(jsonPath("$.hasVotingRights").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/share-capital/top-up: returns 200 with updated shares and balance")
    void testTopUpShareCapital() throws Exception {
        ShareCapitalTopUpResponse mockRes = ShareCapitalTopUpResponse.builder()
                .referenceNo("SC-TOP-12345")
                .amountPaid(new BigDecimal("2000.00"))
                .sharesAdded(20)
                .newPaidUpShares(270)
                .newTotalPaidAmount(new BigDecimal("27000.00"))
                .hasVotingRights(true)
                .membershipType("REGULAR")
                .sourceAccountAvailableBalance(new BigDecimal("48000.00"))
                .timestamp(LocalDateTime.now())
                .build();

        when(shareCapitalService.topUpShareCapital(eq(1001L), any(ShareCapitalTopUpRequest.class))).thenReturn(mockRes);

        ShareCapitalTopUpRequest req = ShareCapitalTopUpRequest.builder()
                .amount(new BigDecimal("2000.00"))
                .build();

        mockMvc.perform(post("/api/v1/accounts/share-capital/top-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceNo").value("SC-TOP-12345"))
                .andExpect(jsonPath("$.sharesAdded").value(20))
                .andExpect(jsonPath("$.newPaidUpShares").value(270))
                .andExpect(jsonPath("$.newTotalPaidAmount").value(27000.0))
                .andExpect(jsonPath("$.hasVotingRights").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/share-capital/dividends: returns 200 with list of past annual dividend declarations")
    void testGetDividendHistory() throws Exception {
        DividendPayoutResponse d1 = DividendPayoutResponse.builder()
                .dividendId(1L)
                .fiscalYear(2025)
                .shareCapitalDividend(new BigDecimal("1812.50"))
                .patronageRefund(new BigDecimal("940.00"))
                .totalPayout(new BigDecimal("2752.50"))
                .disposition("CREDITED_TO_SAVINGS")
                .status("PAID")
                .build();

        when(shareCapitalService.getDividendHistory(1001L)).thenReturn(List.of(d1));

        mockMvc.perform(get("/api/v1/accounts/share-capital/dividends")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fiscalYear").value(2025))
                .andExpect(jsonPath("$[0].totalPayout").value(2752.50))
                .andExpect(jsonPath("$[0].disposition").value("CREDITED_TO_SAVINGS"));
    }
}
