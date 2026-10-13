package com.group2.fse.account_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.account_service.dto.vault.CreateVaultRequest;
import com.group2.fse.account_service.dto.vault.VaultOperationRequest;
import com.group2.fse.account_service.dto.vault.VaultResponse;
import com.group2.fse.account_service.dto.vault.VaultTransactionResponse;
import com.group2.fse.account_service.exception.GlobalExceptionHandler;
import com.group2.fse.account_service.security.jwt.UserPrincipal;
import com.group2.fse.account_service.service.VaultService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Vault Controller REST API Contract Tests")
class AccountVaultControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private VaultService vaultService;

    @InjectMocks
    private AccountVaultController accountVaultController;

    private UserPrincipal customerPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        customerPrincipal = UserPrincipal.create(1001L, "maria_santos", List.of("ROLE_CUSTOMER"));

        mockMvc = MockMvcBuilders.standaloneSetup(accountVaultController)
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
    @DisplayName("[API-VLT-001] GET /api/v1/accounts/vaults -> 200 OK with list of vaults")
    void shouldReturnVaults() throws Exception {
        VaultResponse vault = VaultResponse.builder()
                .id("VAULT-01")
                .vaultId(10L)
                .name("Emergency Fund")
                .targetAmount(new BigDecimal("20000.00"))
                .currentAmount(new BigDecimal("5000.00"))
                .status("ACTIVE")
                .build();

        when(vaultService.getCustomerVaults(1001L)).thenReturn(List.of(vault));

        mockMvc.perform(get("/api/v1/accounts/vaults")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Emergency Fund"))
                .andExpect(jsonPath("$[0].currentAmount").value(5000.00));
    }

    @Test
    @DisplayName("[API-VLT-002] POST /api/v1/accounts/vaults -> 201 Created on valid vault creation")
    void shouldCreateVaultSuccessfully() throws Exception {
        CreateVaultRequest request = CreateVaultRequest.builder()
                .name("Business Expansion")
                .targetAmount(new BigDecimal("50000.00"))
                .targetDate("31 Dec 2026")
                .category("Cooperative")
                .build();

        VaultResponse response = VaultResponse.builder()
                .id("VAULT-02")
                .vaultId(11L)
                .name("Business Expansion")
                .targetAmount(new BigDecimal("50000.00"))
                .currentAmount(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();

        when(vaultService.createVault(eq(1001L), any(CreateVaultRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/vaults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("VAULT-02"))
                .andExpect(jsonPath("$.name").value("Business Expansion"));
    }

    @Test
    @DisplayName("[API-VLT-003] POST /api/v1/accounts/vaults/{vaultId}/allocate -> 200 OK on fund allocation")
    void shouldAllocateFundsSuccessfully() throws Exception {
        VaultOperationRequest request = VaultOperationRequest.builder()
                .amount(new BigDecimal("2500.00"))
                .build();

        VaultResponse response = VaultResponse.builder()
                .id("VAULT-01")
                .vaultId(10L)
                .currentAmount(new BigDecimal("7500.00"))
                .status("ACTIVE")
                .build();

        when(vaultService.allocateFunds(eq(1001L), eq("10"), any(VaultOperationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/vaults/10/allocate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAmount").value(7500.00));
    }

    @Test
    @DisplayName("[API-VLT-004] POST /api/v1/accounts/vaults/{vaultId}/release -> 200 OK on fund release")
    void shouldReleaseFundsSuccessfully() throws Exception {
        VaultOperationRequest request = VaultOperationRequest.builder()
                .amount(new BigDecimal("1000.00"))
                .build();

        VaultResponse response = VaultResponse.builder()
                .id("VAULT-01")
                .vaultId(10L)
                .currentAmount(new BigDecimal("4000.00"))
                .status("ACTIVE")
                .build();

        when(vaultService.releaseFunds(eq(1001L), eq("10"), any(VaultOperationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/vaults/10/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAmount").value(4000.00));
    }

    @Test
    @DisplayName("[API-VLT-005] DELETE /api/v1/accounts/vaults/{vaultId} -> 200 OK on closing vault")
    void shouldCloseVaultSuccessfully() throws Exception {
        VaultResponse response = VaultResponse.builder()
                .id("VAULT-01")
                .vaultId(10L)
                .status("CLOSED")
                .currentAmount(BigDecimal.ZERO)
                .build();

        when(vaultService.closeVault(1001L, "10")).thenReturn(response);

        mockMvc.perform(delete("/api/v1/accounts/vaults/10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("[API-VLT-006] GET /api/v1/accounts/vaults/{vaultId}/transactions -> 200 OK with history")
    void shouldReturnVaultTransactions() throws Exception {
        VaultTransactionResponse tx = VaultTransactionResponse.builder()
                .vaultTxId(101L)
                .transactionType("ALLOCATE")
                .amount(new BigDecimal("2500.00"))
                .balanceAfter(new BigDecimal("7500.00"))
                .referenceNo("VTX-ALC-123456")
                .createdAt(LocalDateTime.now())
                .build();

        when(vaultService.getVaultTransactions(1001L, "10")).thenReturn(List.of(tx));

        mockMvc.perform(get("/api/v1/accounts/vaults/10/transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionType").value("ALLOCATE"))
                .andExpect(jsonPath("$[0].amount").value(2500.00));
    }
}
