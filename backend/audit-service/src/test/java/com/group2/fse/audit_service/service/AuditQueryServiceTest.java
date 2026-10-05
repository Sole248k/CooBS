package com.group2.fse.audit_service.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.group2.fse.audit_service.dto.AuditStatementResponseDto;
import com.group2.fse.audit_service.dto.ChainVerificationResponseDto;
import com.group2.fse.audit_service.dto.ForensicAuditResponseDto;
import com.group2.fse.audit_service.entity.TransactionAudit;
import com.group2.fse.audit_service.exception.ResourceNotFoundException;
import com.group2.fse.audit_service.repository.DailyAuditSealRepository;
import com.group2.fse.audit_service.repository.TransactionAuditRepository;
import com.group2.fse.audit_service.service.impl.AuditQueryServiceImpl;
import com.group2.fse.audit_service.entity.DailyAuditSeal;
import com.group2.fse.audit_service.dto.DailySealResponseDto;
import java.time.LocalDate;
import java.time.Instant;

class AuditQueryServiceTest {

    @Mock
    private TransactionAuditRepository transactionAuditRepository;

    @Mock
    private DailyAuditSealRepository dailyAuditSealRepository;

    @InjectMocks
    private AuditQueryServiceImpl auditQueryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testVerifyChainIntegrity_EmptyRecords() {
        when(transactionAuditRepository.findByAccountIdOrderByAuditIdAsc(1L))
                .thenReturn(Collections.emptyList());

        ChainVerificationResponseDto result = auditQueryService.verifyChainIntegrity(1L);

        assertTrue(result.isChainIntact());
        assertEquals(0, result.getTotalRecordsVerified());
        assertTrue(result.getMessage().contains("trivially intact"));
    }

    @Test
    void testVerifyChainIntegrity_IntactChain() {
        TransactionAudit audit1 = TransactionAudit.builder()
                .auditId(101L)
                .accountId(1L)
                .transactionId(801L)
                .referenceNo("REF-001")
                .transactionType("INITIAL_DEPOSIT")
                .amount(new BigDecimal("100.0000"))
                .oldBalance(new BigDecimal("0.0000"))
                .newBalance(new BigDecimal("100.0000"))
                .previousHash("0000000000000000000000000000000000000000000000000000000000000000")
                .currentHash("hash_1_64chars_abcdef0123456789abcdef0123456789abcdef0123456789abcdef01")
                .eventTimestamp(LocalDateTime.now())
                .build();

        TransactionAudit audit2 = TransactionAudit.builder()
                .auditId(102L)
                .accountId(1L)
                .transactionId(802L)
                .referenceNo("REF-002")
                .transactionType("CREDIT")
                .amount(new BigDecimal("50.0000"))
                .oldBalance(new BigDecimal("100.0000"))
                .newBalance(new BigDecimal("150.0000"))
                .previousHash("hash_1_64chars_abcdef0123456789abcdef0123456789abcdef0123456789abcdef01")
                .currentHash("hash_2_64chars_abcdef0123456789abcdef0123456789abcdef0123456789abcdef02")
                .eventTimestamp(LocalDateTime.now())
                .build();

        when(transactionAuditRepository.findByAccountIdOrderByAuditIdAsc(1L))
                .thenReturn(List.of(audit1, audit2));

        ChainVerificationResponseDto result = auditQueryService.verifyChainIntegrity(1L);

        assertTrue(result.isChainIntact());
        assertEquals(2, result.getTotalRecordsVerified());
        assertEquals("hash_2_64chars_abcdef0123456789abcdef0123456789abcdef0123456789abcdef02", result.getLatestHash());
    }

    @Test
    void testVerifyChainIntegrity_BrokenLinkage() {
        TransactionAudit audit1 = TransactionAudit.builder()
                .auditId(101L)
                .accountId(1L)
                .transactionId(801L)
                .referenceNo("REF-001")
                .transactionType("INITIAL_DEPOSIT")
                .amount(new BigDecimal("100.0000"))
                .oldBalance(new BigDecimal("0.0000"))
                .newBalance(new BigDecimal("100.0000"))
                .previousHash("0000000000000000000000000000000000000000000000000000000000000000")
                .currentHash("hash_1")
                .eventTimestamp(LocalDateTime.now())
                .build();

        // Previous hash does NOT match audit1.currentHash
        TransactionAudit audit2 = TransactionAudit.builder()
                .auditId(102L)
                .accountId(1L)
                .transactionId(802L)
                .referenceNo("REF-002")
                .transactionType("CREDIT")
                .amount(new BigDecimal("50.0000"))
                .oldBalance(new BigDecimal("100.0000"))
                .newBalance(new BigDecimal("150.0000"))
                .previousHash("tampered_hash_link")
                .currentHash("hash_2")
                .eventTimestamp(LocalDateTime.now())
                .build();

        when(transactionAuditRepository.findByAccountIdOrderByAuditIdAsc(1L))
                .thenReturn(List.of(audit1, audit2));

        ChainVerificationResponseDto result = auditQueryService.verifyChainIntegrity(1L);

        assertFalse(result.isChainIntact());
        assertEquals(1, result.getTotalRecordsVerified());
        assertTrue(result.getMessage().contains("Chain linkage mismatch at audit ID 102"));
    }

    @Test
    void testGetTransactionForensics_Success() {
        TransactionAudit audit = TransactionAudit.builder()
                .auditId(101L)
                .accountId(1L)
                .transactionId(801L)
                .referenceNo("REF-801")
                .transactionType("CREDIT")
                .amount(new BigDecimal("250.0000"))
                .oldBalance(new BigDecimal("1000.0000"))
                .newBalance(new BigDecimal("1250.0000"))
                .previousHash("0000000000000000000000000000000000000000000000000000000000000000")
                .currentHash("current_hash_801")
                .actorId(10L)
                .clientIp("192.168.1.100")
                .eventTimestamp(LocalDateTime.now())
                .build();

        when(transactionAuditRepository.findByTransactionId(801L))
                .thenReturn(Optional.of(audit));

        ForensicAuditResponseDto dto = auditQueryService.getTransactionForensics(801L);

        assertNotNull(dto);
        assertEquals(801L, dto.getTransactionId());
        assertEquals("REF-801", dto.getReferenceNo());
        assertEquals(new BigDecimal("250.0000"), dto.getAmount());
        assertEquals("192.168.1.100", dto.getClientIp());
    }

    @Test
    void testGetTransactionForensics_NotFoundThrowsResourceNotFoundException() {
        when(transactionAuditRepository.findByTransactionId(999L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                auditQueryService.getTransactionForensics(999L));
    }

    @Test
    void testGetAccountStatement_Success() {
        TransactionAudit audit = TransactionAudit.builder()
                .auditId(101L)
                .accountId(1L)
                .transactionId(801L)
                .referenceNo("REF-801")
                .transactionType("CREDIT")
                .amount(new BigDecimal("250.0000"))
                .oldBalance(new BigDecimal("1000.0000"))
                .newBalance(new BigDecimal("1250.0000"))
                .currentHash("current_hash_801")
                .eventTimestamp(LocalDateTime.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(transactionAuditRepository.findByAccountId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(audit), pageable, 1));

        Page<AuditStatementResponseDto> page = auditQueryService.getAccountStatement(1L, null, null, pageable);

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals("REF-801", page.getContent().get(0).getReferenceNo());
    }

    @Test
    void testComputeSha256_ProducesValidDigest() {
        String hash = AuditQueryServiceImpl.computeSha256("test_payload");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void testGetDailySeal_Success() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        DailyAuditSeal seal = DailyAuditSeal.builder()
                .sealId(1L)
                .businessDate(date)
                .lastAuditId(48L)
                .sealHash("seal_hash_001")
                .sealedAt(Instant.parse("2026-10-03T17:00:06Z"))
                .build();

        when(dailyAuditSealRepository.findByBusinessDate(date)).thenReturn(Optional.of(seal));

        DailySealResponseDto result = auditQueryService.getDailySeal(date);

        assertNotNull(result);
        assertEquals(date, result.getBusinessDate());
        assertEquals(48L, result.getLastAuditId());
        assertEquals("seal_hash_001", result.getSealHash());
    }

    @Test
    void testGetDailySeal_NotFound() {
        LocalDate date = LocalDate.of(2026, 10, 4);
        when(dailyAuditSealRepository.findByBusinessDate(date)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> auditQueryService.getDailySeal(date));
    }
}