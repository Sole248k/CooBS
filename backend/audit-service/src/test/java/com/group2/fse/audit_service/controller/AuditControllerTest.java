package com.group2.fse.audit_service.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.group2.fse.audit_service.dto.AuditStatementResponseDto;
import com.group2.fse.audit_service.dto.ChainVerificationResponseDto;
import com.group2.fse.audit_service.dto.ForensicAuditResponseDto;
import com.group2.fse.audit_service.exception.GlobalExceptionHandler;
import com.group2.fse.audit_service.exception.ResourceNotFoundException;
import com.group2.fse.audit_service.service.AuditQueryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Audit Controller Unit & RFC-7807 Error Handling Tests")
class AuditControllerTest {

    @Mock
    private AuditQueryService auditQueryService;

    @InjectMocks
    private AuditController auditController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(auditController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/audit/accounts/{accountId}/statement - Returns 200 OK with statement")
    void testGetAccountStatement_Success() throws Exception {
        AuditStatementResponseDto item = AuditStatementResponseDto.builder()
                .auditId(1L)
                .transactionId(101L)
                .accountId(1L)
                .referenceNo("REF-001")
                .transactionType("INITIAL_DEPOSIT")
                .amount(new BigDecimal("50000.0000"))
                .oldBalance(new BigDecimal("0.0000"))
                .newBalance(new BigDecimal("50000.0000"))
                .currentHash("hash_genesis")
                .eventTimestamp(LocalDateTime.now())
                .build();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(auditQueryService.getAccountStatement(eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        mockMvc.perform(get("/api/v1/audit/accounts/1/statement")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].referenceNo").value("REF-001"))
                .andExpect(jsonPath("$.content[0].amount").value(50000.0000))
                .andExpect(jsonPath("$.content[0].transactionType").value("INITIAL_DEPOSIT"));
    }

    @Test
    @DisplayName("GET /api/v1/audit/transactions/{transactionId} - Returns 200 OK with forensics")
    void testGetTransactionForensics_Success() throws Exception {
        ForensicAuditResponseDto forensics = ForensicAuditResponseDto.builder()
                .auditId(1L)
                .transactionId(101L)
                .accountId(1L)
                .referenceNo("REF-001")
                .transactionType("CREDIT")
                .amount(new BigDecimal("100.0000"))
                .oldBalance(new BigDecimal("50000.0000"))
                .newBalance(new BigDecimal("50100.0000"))
                .actorId(10L)
                .clientIp("127.0.0.1")
                .currentHash("current_hash_admin")
                .build();

        when(auditQueryService.getTransactionForensics(101L))
                .thenReturn(forensics);

        mockMvc.perform(get("/api/v1/audit/transactions/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(101))
                .andExpect(jsonPath("$.referenceNo").value("REF-001"))
                .andExpect(jsonPath("$.clientIp").value("127.0.0.1"));
    }

    @Test
    @DisplayName("GET /api/v1/audit/transactions/{transactionId} - Returns 404 RFC-7807 when not found")
    void testGetTransactionForensics_NotFoundReturnsRfc7807() throws Exception {
        when(auditQueryService.getTransactionForensics(999L))
                .thenThrow(new ResourceNotFoundException("Audit record not found for transaction ID: 999"));

        mockMvc.perform(get("/api/v1/audit/transactions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.corebank.local/errors/RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Audit record not found for transaction ID: 999"))
                .andExpect(jsonPath("$.instance").value("/api/v1/audit/transactions/999"));
    }

    @Test
    @DisplayName("GET /api/v1/audit/verify-chain/{accountId} - Returns 200 OK with intact verification")
    void testVerifyChain_Intact() throws Exception {
        ChainVerificationResponseDto verification = ChainVerificationResponseDto.builder()
                .accountId(1L)
                .totalRecordsVerified(10)
                .isChainIntact(true)
                .latestHash("latest_hash_001")
                .verifiedAt(LocalDateTime.now())
                .message("Audit chain integrity verified successfully.")
                .build();

        when(auditQueryService.verifyChainIntegrity(1L))
                .thenReturn(verification);

        mockMvc.perform(get("/api/v1/audit/verify-chain/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isChainIntact").value(true))
                .andExpect(jsonPath("$.totalRecordsVerified").value(10))
                .andExpect(jsonPath("$.latestHash").value("latest_hash_001"));
    }

    @Test
    @DisplayName("GET /api/v1/audit/verify-chain/{accountId} - Returns 200 OK with broken verification result")
    void testVerifyChain_BrokenChain() throws Exception {
        ChainVerificationResponseDto verification = ChainVerificationResponseDto.builder()
                .accountId(1L)
                .totalRecordsVerified(4)
                .isChainIntact(false)
                .latestHash("tampered_hash")
                .verifiedAt(LocalDateTime.now())
                .message("Chain linkage mismatch at audit ID 5")
                .build();

        when(auditQueryService.verifyChainIntegrity(1L))
                .thenReturn(verification);

        mockMvc.perform(get("/api/v1/audit/verify-chain/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isChainIntact").value(false))
                .andExpect(jsonPath("$.message").value("Chain linkage mismatch at audit ID 5"));
    }

    @Test
    @DisplayName("GET /api/v1/audit/seals/{businessDate} - Returns 200 OK with seal details")
    void testGetDailySeal_Success() throws Exception {
        com.group2.fse.audit_service.dto.DailySealResponseDto sealDto = com.group2.fse.audit_service.dto.DailySealResponseDto.builder()
                .businessDate(java.time.LocalDate.of(2026, 10, 3))
                .lastAuditId(48L)
                .sealHash("c7d1e2f3a4b5c6d7e8f90123456789abcdef0123456789abcdef0123456789ab")
                .sealedAt(java.time.Instant.parse("2026-10-03T17:00:06Z"))
                .build();

        when(auditQueryService.getDailySeal(java.time.LocalDate.of(2026, 10, 3))).thenReturn(sealDto);

        mockMvc.perform(get("/api/v1/audit/seals/2026-10-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value("2026-10-03"))
                .andExpect(jsonPath("$.lastAuditId").value(48))
                .andExpect(jsonPath("$.sealHash").value("c7d1e2f3a4b5c6d7e8f90123456789abcdef0123456789abcdef0123456789ab"));
    }

    @Test
    @DisplayName("GET /api/v1/audit/seals/{businessDate} - Returns 404 when seal not found")
    void testGetDailySeal_NotFound() throws Exception {
        when(auditQueryService.getDailySeal(java.time.LocalDate.of(2026, 10, 4)))
                .thenThrow(new ResourceNotFoundException("Daily audit seal not found for date: 2026-10-04"));

        mockMvc.perform(get("/api/v1/audit/seals/2026-10-04"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Daily audit seal not found for date: 2026-10-04"));
    }
}
