package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.SpendingBreakdownDto;
import com.group2.fse.transaction_service.dto.TransactionSummaryDto;
import com.group2.fse.transaction_service.security.jwt.JwtTokenProvider;
import com.group2.fse.transaction_service.service.TransactionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoryInternalController T8/T9 Handshake H10 Tests")
class HistoryInternalControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TransactionHistoryService transactionHistoryService;

    @InjectMocks
    private HistoryInternalController historyInternalController;

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(historyInternalController)
                .build();

        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
        jwtTokenProvider.init();

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /internal/v1/transactions returns recent transactions for customerId")
    void getRecentTransactions_returnsList() throws Exception {
        TransactionSummaryDto dto1 = TransactionSummaryDto.builder()
                .transactionId(UUID.randomUUID())
                .referenceNo("TXN-1001")
                .direction("OUT")
                .counterpartyName("Electric Co")
                .counterpartyAccountNumber("ACC-2001")
                .amount("1500.00")
                .currency("PHP")
                .category("UTILITIES")
                .status("COMPLETED")
                .createdAt("2026-10-07T10:00:00Z")
                .build();

        when(transactionHistoryService.getRecentTransactions(101L, 5))
                .thenReturn(List.of(dto1));

        mockMvc.perform(get("/internal/v1/transactions")
                        .param("customerId", "101")
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].referenceNo").value("TXN-1001"))
                .andExpect(jsonPath("$[0].amount").value("1500.00"))
                .andExpect(jsonPath("$[0].category").value("UTILITIES"));

        verify(transactionHistoryService, times(1)).getRecentTransactions(101L, 5);
    }

    @Test
    @DisplayName("GET /internal/v1/transactions/spending returns spending breakdown for customerId")
    void getSpendingBreakdown_returnsBreakdown() throws Exception {
        SpendingBreakdownDto breakdown = SpendingBreakdownDto.builder()
                .periodDays(30)
                .items(List.of(
                        SpendingBreakdownDto.SpendingItemDto.builder()
                                .category("FOOD")
                                .amount("3500.00")
                                .count(4)
                                .build(),
                        SpendingBreakdownDto.SpendingItemDto.builder()
                                .category("UTILITIES")
                                .amount("1500.00")
                                .count(1)
                                .build()
                ))
                .build();

        when(transactionHistoryService.getSpendingBreakdown(101L, 30))
                .thenReturn(breakdown);

        mockMvc.perform(get("/internal/v1/transactions/spending")
                        .param("customerId", "101")
                        .param("days", "30")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(30))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].category").value("FOOD"))
                .andExpect(jsonPath("$.items[0].amount").value("3500.00"))
                .andExpect(jsonPath("$.items[0].count").value(4));

        verify(transactionHistoryService, times(1)).getSpendingBreakdown(101L, 30);
    }

    @Test
    @DisplayName("Verify JWT Token with ROLE_SERVICE generates correct authorities for internal access")
    void verifyJwtTokenValidation_serviceRole() {
        String serviceToken = jwtTokenProvider.generateToken(0L, "customer-service", List.of("ROLE_SERVICE"));

        assertThat(jwtTokenProvider.validateToken(serviceToken)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(serviceToken)).isEqualTo("customer-service");
        assertThat(jwtTokenProvider.getRoles(serviceToken)).contains("ROLE_SERVICE");

        var authentication = jwtTokenProvider.getAuthentication(serviceToken);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
    }

    @Test
    @DisplayName("Verify JWT Token with ROLE_CUSTOMER does not grant ROLE_SERVICE")
    void verifyJwtTokenValidation_customerRole_doesNotHaveServiceRole() {
        String customerToken = jwtTokenProvider.generateToken(101L, "john_doe", List.of("ROLE_CUSTOMER"));

        assertThat(jwtTokenProvider.validateToken(customerToken)).isTrue();
        var authentication = jwtTokenProvider.getAuthentication(customerToken);
        assertThat(authentication.getAuthorities())
                .noneMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
    }
}
