package com.group2.fse.account_service.controller;

import com.group2.fse.account_service.dto.AccountResponse;
import com.group2.fse.account_service.security.filter.JwtAuthenticationFilter;
import com.group2.fse.account_service.security.jwt.JwtTokenProvider;
import com.group2.fse.account_service.security.jwt.UserPrincipal;
import com.group2.fse.account_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountInternalController Hybrid 360 & Internal Endpoints Tests")
class AccountInternalControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountInternalController accountInternalController;

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(accountInternalController)
                .build();

        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
        jwtTokenProvider.init();

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /internal/v1/customers/{customerId}/accounts returns list of accounts for 360 aggregation")
    void getAccountsByCustomerId_returnsAccountList() throws Exception {
        AccountResponse acc1 = AccountResponse.builder()
                .accountId(1L)
                .accountNumber("ACC_10000001")
                .accountType("SAVINGS")
                .currency("PHP")
                .status("ACTIVE")
                .availableBalance(new BigDecimal("15000.00"))
                .customerId(10L)
                .createdAt(LocalDateTime.now())
                .build();

        AccountResponse acc2 = AccountResponse.builder()
                .accountId(2L)
                .accountNumber("LN_10000002")
                .accountType("LOAN")
                .currency("PHP")
                .status("ACTIVE")
                .availableBalance(new BigDecimal("50000.00"))
                .customerId(10L)
                .createdAt(LocalDateTime.now())
                .build();

        when(accountService.getCustomerAccounts(10L)).thenReturn(List.of(acc1, acc2));

        mockMvc.perform(get("/internal/v1/customers/10/accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accountId").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value("ACC_10000001"))
                .andExpect(jsonPath("$[0].accountType").value("SAVINGS"))
                .andExpect(jsonPath("$[0].availableBalance").value(15000.00))
                .andExpect(jsonPath("$[1].accountId").value(2))
                .andExpect(jsonPath("$[1].accountNumber").value("LN_10000002"))
                .andExpect(jsonPath("$[1].accountType").value("LOAN"))
                .andExpect(jsonPath("$[1].availableBalance").value(50000.00));

        verify(accountService, times(1)).getCustomerAccounts(10L);
    }

    @Test
    @DisplayName("GET /internal/v1/accounts/{accountId} returns single account details")
    void getAccountByIdInternal_returnsAccount() throws Exception {
        AccountResponse acc = AccountResponse.builder()
                .accountId(5L)
                .accountNumber("ACC_10000005")
                .accountType("SAVINGS")
                .currency("PHP")
                .status("ACTIVE")
                .availableBalance(new BigDecimal("2500.00"))
                .customerId(10L)
                .build();

        when(accountService.getAccountById(5L, null, true)).thenReturn(acc);

        mockMvc.perform(get("/internal/v1/accounts/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(5))
                .andExpect(jsonPath("$.accountNumber").value("ACC_10000005"));

        verify(accountService, times(1)).getAccountById(5L, null, true);
    }

    @Test
    @DisplayName("GET /internal/v1/accounts/by-number/{accountNumber} returns account by account number")
    void getAccountByNumberInternal_returnsAccount() throws Exception {
        AccountResponse acc = AccountResponse.builder()
                .accountId(5L)
                .accountNumber("ACC_10000005")
                .accountType("SAVINGS")
                .currency("PHP")
                .status("ACTIVE")
                .availableBalance(new BigDecimal("2500.00"))
                .customerId(10L)
                .build();

        when(accountService.getAccountByNumber("ACC_10000005")).thenReturn(acc);

        mockMvc.perform(get("/internal/v1/accounts/by-number/ACC_10000005")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC_10000005"));

        verify(accountService, times(1)).getAccountByNumber("ACC_10000005");
    }

    @Test
    @DisplayName("Verify JWT Token with ROLE_SERVICE generates correct authorities for internal access")
    void verifyJwtTokenValidation_serviceRole() {
        String serviceToken = jwtTokenProvider.generateToken("customer-service", 0L, List.of("ROLE_SERVICE"), "jti-1", 3600000L);

        assertThat(jwtTokenProvider.validateToken(serviceToken)).isTrue();
        assertThat(jwtTokenProvider.getUsername(serviceToken)).isEqualTo("customer-service");
        assertThat(jwtTokenProvider.getRoles(serviceToken)).contains("ROLE_SERVICE");

        var authentication = jwtTokenProvider.getAuthentication(serviceToken);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
    }

    @Test
    @DisplayName("Verify JWT Token with ROLE_CUSTOMER does not grant ROLE_SERVICE")
    void verifyJwtTokenValidation_customerRole_doesNotHaveServiceRole() {
        String customerToken = jwtTokenProvider.generateToken("john_doe", 101L, List.of("ROLE_CUSTOMER"), "jti-2", 3600000L);

        assertThat(jwtTokenProvider.validateToken(customerToken)).isTrue();
        var authentication = jwtTokenProvider.getAuthentication(customerToken);
        assertThat(authentication.getAuthorities())
                .noneMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
    }
}
