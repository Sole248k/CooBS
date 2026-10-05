package com.group2.fse.audit_service.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.group2.fse.audit_service.dto.AuditStatementResponseDto;
import com.group2.fse.audit_service.dto.ChainVerificationResponseDto;
import com.group2.fse.audit_service.dto.ForensicAuditResponseDto;
import com.group2.fse.audit_service.service.AuditQueryService;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/accounts/{accountId}/statement")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<Page<AuditStatementResponseDto>> getAccountStatement(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTimestamp,desc") String sort) {

        String[] sortParams = sort.split(",");
        String sortProperty = sortParams[0];
        Sort.Direction direction = (sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));

        Page<AuditStatementResponseDto> statement = auditQueryService.getAccountStatement(
                accountId, startDate, endDate, pageable);

        return ResponseEntity.ok(statement);
    }

    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ForensicAuditResponseDto> getTransactionForensics(@PathVariable Long transactionId) {
        ForensicAuditResponseDto forensics = auditQueryService.getTransactionForensics(transactionId);
        return ResponseEntity.ok(forensics);
    }

    @GetMapping("/verify-chain/{accountId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<ChainVerificationResponseDto> verifyChainIntegrity(@PathVariable Long accountId) {
        ChainVerificationResponseDto verification = auditQueryService.verifyChainIntegrity(accountId);
        return ResponseEntity.ok(verification);
    }

    /**
     * U4: Get Daily Audit Seal for a given business date.
     * Roles: CUSTOMER, TELLER, ADMIN, AUDITOR
     */
    @GetMapping("/seals/{businessDate}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<com.group2.fse.audit_service.dto.DailySealResponseDto> getDailySeal(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate businessDate) {
        com.group2.fse.audit_service.dto.DailySealResponseDto response = auditQueryService.getDailySeal(businessDate);
        return ResponseEntity.ok(response);
    }
}