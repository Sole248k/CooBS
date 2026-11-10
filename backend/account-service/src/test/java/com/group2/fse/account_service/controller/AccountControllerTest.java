package com.group2.fse.account_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.account_service.dto.*;
import com.group2.fse.account_service.exception.AccountNonZeroBalanceException;
import com.group2.fse.account_service.exception.GlobalExceptionHandler;
import com.group2.fse.account_service.security.jwt.UserPrincipal;
import com.group2.fse.account_service.service.AccountClosureService;
import com.group2.fse.account_service.service.AccountFlagService;
import com.group2.fse.account_service.service.AccountService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Controller REST API Contract Tests")
class AccountControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountClosureService accountClosureService;

    @Mock
    private AccountFlagService accountFlagService;

    @InjectMocks
    private AccountController accountController;

    private UserPrincipal customerPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        customerPrincipal = UserPrincipal.create(101L, "john_doe", List.of("ROLE_CUSTOMER"));

        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return customerPrincipal;
                    }
                })
                .build();
    }

    @Test
    @DisplayName("[API-ACC-001] POST /api/v1/accounts -> 201 Created on valid account opening")
    void shouldOpenAccountSuccessfully() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .customerId(101L)
                .accountType("SAVINGS")
                .currency("PHP")
                .build();

        AccountResponse response = AccountResponse.builder()
                .accountId(305L)
                .customerId(101L)
                .accountNumber("ACC_10000005")
                .accountType("SAVINGS")
                .currency("PHP")
                .status("ACTIVE")
                .availableBalance(new BigDecimal("0.0000"))
                .createdAt(LocalDateTime.now())
                .build();

        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(305))
                .andExpect(jsonPath("$.accountNumber").value("ACC_10000005"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.availableBalance").value(0.0000));
    }

    @Test
    @DisplayName("[API-ACC-002] GET /api/v1/accounts/my-accounts -> 200 OK list of customer's deposit accounts")
    void shouldGetCustomerAccounts() throws Exception {
        AccountResponse response = AccountResponse.builder()
                .accountId(1L)
                .customerId(101L)
                .accountNumber("ACC_10000001")
                .accountType("SAVINGS")
                .currency("PHP")
                .status("ACTIVE")
                .availableBalance(new BigDecimal("50000.0000"))
                .build();

        when(accountService.getCustomerAccounts(101L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/accounts/my-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value("ACC_10000001"))
                .andExpect(jsonPath("$[0].availableBalance").value(50000.0));
    }

    @Test
    @DisplayName("[API-ACC-003] POST /api/v1/accounts/{id}/closure-request -> 202 Accepted on valid zero-balance closure request")
    void shouldAcceptClosureRequest() throws Exception {
        AccountClosureRequestDto request = new AccountClosureRequestDto("Closing account");
        ClosureRequestResponse response = ClosureRequestResponse.builder()
                .closureRequestId(88L)
                .accountId(4L)
                .accountNumber("ACC_10000004")
                .status("PENDING")
                .availableBalance(BigDecimal.ZERO.setScale(4))
                .message("Account closure request submitted for bank review. Account is pending closure.")
                .build();

        when(accountClosureService.submitClosureRequest(eq(4L), eq(101L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/4/closure-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.closureRequestId").value(88))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("[API-ACC-004] POST /api/v1/accounts/{id}/closure-request -> 400 Bad Request if balance is non-zero")
    void shouldRejectClosureRequestWhenNonZeroBalance() throws Exception {
        when(accountClosureService.submitClosureRequest(eq(4L), eq(101L), any()))
                .thenThrow(new AccountNonZeroBalanceException("ACC_10000004", new BigDecimal("12500.0000")));

        mockMvc.perform(post("/api/v1/accounts/4/closure-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NON_ZERO_BALANCE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.availableBalance").value(12500.0));
    }

    @Test
    @DisplayName("[API-ACC-005] POST /api/v1/accounts/closure-requests/{id}/approve -> 200 OK closes account but keeps customer ACTIVE")
    void shouldApproveClosureRequest() throws Exception {
        ClosureRequestResponse response = ClosureRequestResponse.builder()
                .closureRequestId(88L)
                .accountId(4L)
                .accountNumber("ACC_10000004")
                .accountStatus("CLOSED")
                .customerStatus("ACTIVE")
                .approvedBy(101L)
                .message("Account ACC_10000004 has been closed successfully. Customer profile remains active.")
                .build();

        when(accountClosureService.approveClosureRequest(88L, 101L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/closure-requests/88/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closureRequestId").value(88))
                .andExpect(jsonPath("$.accountStatus").value("CLOSED"))
                .andExpect(jsonPath("$.customerStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("[API-ACC-006] POST /api/v1/accounts/{id}/flags -> 201 Created on placing hold")
    void shouldAddFlagSuccessfully() throws Exception {
        AccountFlagRequest request = new AccountFlagRequest("Court order");
        AccountFlagResponse response = AccountFlagResponse.builder()
                .flagId(12L)
                .accountId(1L)
                .reason("Court order")
                .status("ACTIVE")
                .flaggedBy(101L)
                .message("Administrative hold imposed successfully.")
                .build();

        when(accountFlagService.addFlag(eq(1L), any(AccountFlagRequest.class), eq(101L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagId").value(12))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("[API-ACC-007] DELETE /api/v1/accounts/{id}/flags/{flagId} -> 200 OK lifts hold")
    void shouldRemoveFlagSuccessfully() throws Exception {
        AccountFlagResponse response = AccountFlagResponse.builder()
                .flagId(12L)
                .accountId(1L)
                .status("REMOVED")
                .message("Administrative hold lifted successfully.")
                .build();

        when(accountFlagService.removeFlag(1L, 12L, 101L)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/accounts/1/flags/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagId").value(12))
                .andExpect(jsonPath("$.status").value("REMOVED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/accounts/{id}/status takes ACTIVE, FROZEN or DORMANT; CLOSED and any other text are 400")
    void shouldRefuseAStatusThatCannotBeSetDirectly() throws Exception {
        when(accountService.updateAccountStatus(1L, "frozen"))
                .thenReturn(AccountResponse.builder().accountId(1L).status("FROZEN").build());

        mockMvc.perform(patch("/api/v1/accounts/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"frozen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        for (String refused : List.of("CLOSED", "FROZE", "")) {
            mockMvc.perform(patch("/api/v1/accounts/1/status")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + refused + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verify(accountService, times(1)).updateAccountStatus(any(), any());
    }
}
