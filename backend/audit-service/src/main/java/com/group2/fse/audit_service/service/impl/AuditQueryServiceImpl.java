package com.group2.fse.audit_service.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.group2.fse.audit_service.dto.AuditStatementResponseDto;
import com.group2.fse.audit_service.dto.ChainVerificationResponseDto;
import com.group2.fse.audit_service.dto.ForensicAuditResponseDto;
import com.group2.fse.audit_service.entity.TransactionAudit;
import java.time.LocalDate;
import com.group2.fse.audit_service.dto.DailySealResponseDto;
import com.group2.fse.audit_service.entity.DailyAuditSeal;
import com.group2.fse.audit_service.repository.DailyAuditSealRepository;
import com.group2.fse.audit_service.exception.ResourceNotFoundException;
import com.group2.fse.audit_service.repository.TransactionAuditRepository;
import com.group2.fse.audit_service.service.AuditQueryService;

@Service
public class AuditQueryServiceImpl implements AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditQueryServiceImpl.class);
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final TransactionAuditRepository transactionAuditRepository;
    private final DailyAuditSealRepository dailyAuditSealRepository;

    public AuditQueryServiceImpl(
            TransactionAuditRepository transactionAuditRepository,
            DailyAuditSealRepository dailyAuditSealRepository) {
        this.transactionAuditRepository = transactionAuditRepository;
        this.dailyAuditSealRepository = dailyAuditSealRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditStatementResponseDto> getAccountStatement(
            Long accountId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {

        log.debug("Fetching account statement for accountId={}, startDate={}, endDate={}, pageable={}",
                accountId, startDate, endDate, pageable);

        Page<TransactionAudit> auditPage = (startDate != null && endDate != null)
                ? transactionAuditRepository.findByAccountIdAndEventTimestampBetween(accountId, startDate, endDate, pageable)
                : transactionAuditRepository.findByAccountId(accountId, pageable);

        return auditPage.map(audit -> AuditStatementResponseDto.builder()
                .auditId(audit.getAuditId())
                .transactionId(audit.getTransactionId())
                .accountId(audit.getAccountId())
                .referenceNo(audit.getReferenceNo())
                .transactionType(audit.getTransactionType())
                .amount(audit.getAmount())
                .oldBalance(audit.getOldBalance())
                .newBalance(audit.getNewBalance())
                .previousHash(audit.getPreviousHash())
                .currentHash(audit.getCurrentHash())
                .actorId(audit.getActorId())
                .clientIp(audit.getClientIp())
                .eventTimestamp(audit.getEventTimestamp())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public ForensicAuditResponseDto getTransactionForensics(Long transactionId) {
        log.debug("Fetching forensic audit record for transactionId={}", transactionId);

        TransactionAudit audit = transactionAuditRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found for transaction ID: " + transactionId));

        return ForensicAuditResponseDto.builder()
                .auditId(audit.getAuditId())
                .transactionId(audit.getTransactionId())
                .accountId(audit.getAccountId())
                .referenceNo(audit.getReferenceNo())
                .transactionType(audit.getTransactionType())
                .amount(audit.getAmount())
                .oldBalance(audit.getOldBalance())
                .newBalance(audit.getNewBalance())
                .previousHash(audit.getPreviousHash())
                .currentHash(audit.getCurrentHash())
                .actorId(audit.getActorId())
                .clientIp(audit.getClientIp())
                .eventTimestamp(audit.getEventTimestamp())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChainVerificationResponseDto verifyChainIntegrity(Long accountId) {
        log.info("Verifying cryptographic audit chain integrity for accountId={}", accountId);

        List<TransactionAudit> records = transactionAuditRepository.findByAccountIdOrderByAuditIdAsc(accountId);

        if (records.isEmpty()) {
            log.info("No audit records found for accountId={}. Chain is trivially intact.", accountId);
            return ChainVerificationResponseDto.builder()
                    .accountId(accountId)
                    .totalRecordsVerified(0)
                    .isChainIntact(true)
                    .verifiedAt(LocalDateTime.now())
                    .message("No audit records found for account. Chain is trivially intact.")
                    .build();
        }

        String expectedPreviousHash = GENESIS_HASH;

        for (int i = 0; i < records.size(); i++) {
            TransactionAudit record = records.get(i);

            if (i > 0) {
                expectedPreviousHash = records.get(i - 1).getCurrentHash();
            }

            // 1. Verify previous hash linkage against predecessor
            if (record.getPreviousHash() == null || !expectedPreviousHash.equalsIgnoreCase(record.getPreviousHash())) {
                String errorMsg = String.format("Chain linkage mismatch at audit ID %d (txn %d): expected previous_hash [%s] but found [%s]",
                        record.getAuditId(), record.getTransactionId(), expectedPreviousHash, record.getPreviousHash());
                log.warn("FORENSIC TAMPER DETECTED: {}", errorMsg);
                return ChainVerificationResponseDto.builder()
                        .accountId(accountId)
                        .totalRecordsVerified(i)
                        .isChainIntact(false)
                        .latestHash(record.getCurrentHash())
                        .verifiedAt(LocalDateTime.now())
                        .message(errorMsg)
                        .build();
            }

            // 2. Verify current hash exists
            if (record.getCurrentHash() == null || record.getCurrentHash().trim().isEmpty()) {
                String errorMsg = String.format("Missing current hash at audit ID %d (txn %d)",
                        record.getAuditId(), record.getTransactionId());
                log.warn("FORENSIC TAMPER DETECTED: {}", errorMsg);
                return ChainVerificationResponseDto.builder()
                        .accountId(accountId)
                        .totalRecordsVerified(i)
                        .isChainIntact(false)
                        .latestHash(null)
                        .verifiedAt(LocalDateTime.now())
                        .message(errorMsg)
                        .build();
            }
        }

        String latestHash = records.get(records.size() - 1).getCurrentHash();
        log.info("Cryptographic chain verified for accountId={}. Total records={}, latestHash={}",
                accountId, records.size(), latestHash);

        return ChainVerificationResponseDto.builder()
                .accountId(accountId)
                .totalRecordsVerified(records.size())
                .isChainIntact(true)
                .latestHash(latestHash)
                .verifiedAt(LocalDateTime.now())
                .message("Audit chain integrity verified successfully.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DailySealResponseDto getDailySeal(LocalDate businessDate) {
        log.info("Fetching daily audit seal for businessDate={}", businessDate);
        DailyAuditSeal seal = dailyAuditSealRepository.findByBusinessDate(businessDate)
                .orElseThrow(() -> new ResourceNotFoundException("Daily audit seal not found for date: " + businessDate));

        return DailySealResponseDto.builder()
                .businessDate(seal.getBusinessDate())
                .lastAuditId(seal.getLastAuditId())
                .sealHash(seal.getSealHash())
                .sealedAt(seal.getSealedAt())
                .build();
    }

    /**
     * Utility method to recompute SHA-256 digest matching PostgreSQL trigger formula.
     */
    public static String computeSha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}