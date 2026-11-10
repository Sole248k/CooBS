package com.group2.fse.account_service.controller;

import com.group2.fse.account_service.dto.vault.CreateVaultRequest;
import com.group2.fse.account_service.dto.vault.VaultOperationRequest;
import com.group2.fse.account_service.dto.vault.VaultResponse;
import com.group2.fse.account_service.dto.vault.VaultTransactionResponse;
import com.group2.fse.account_service.security.jwt.UserPrincipal;
import com.group2.fse.account_service.service.VaultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts/vaults")
@RequiredArgsConstructor
public class AccountVaultController {

    private final VaultService vaultService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<VaultResponse>> getMyVaults(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching vaults for customer ID {}", principal != null ? principal.getUserId() : null);
        List<VaultResponse> vaults = vaultService.getCustomerVaults(principal.getUserId());
        return ResponseEntity.ok(vaults);
    }

    @GetMapping("/{vaultId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<VaultResponse> getVaultById(
            @PathVariable String vaultId,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching vault {} for customer ID {}", vaultId, principal != null ? principal.getUserId() : null);
        VaultResponse vault = vaultService.getVault(principal.getUserId(), vaultId);
        return ResponseEntity.ok(vault);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<VaultResponse> createVault(
            @Valid @RequestBody CreateVaultRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Creating vault '{}' for customer ID {}", request.getName(), principal != null ? principal.getUserId() : null);
        VaultResponse created = vaultService.createVault(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{vaultId}/allocate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<VaultResponse> allocateFunds(
            @PathVariable String vaultId,
            @Valid @RequestBody VaultOperationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Allocating {} to vault {} for customer ID {}", request.getAmount(), vaultId, principal != null ? principal.getUserId() : null);
        VaultResponse updated = vaultService.allocateFunds(principal.getUserId(), vaultId, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{vaultId}/release")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<VaultResponse> releaseFunds(
            @PathVariable String vaultId,
            @Valid @RequestBody VaultOperationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Releasing {} from vault {} for customer ID {}", request.getAmount(), vaultId, principal != null ? principal.getUserId() : null);
        VaultResponse updated = vaultService.releaseFunds(principal.getUserId(), vaultId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{vaultId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<VaultResponse> closeVault(
            @PathVariable String vaultId,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Closing vault {} for customer ID {}", vaultId, principal != null ? principal.getUserId() : null);
        VaultResponse closed = vaultService.closeVault(principal.getUserId(), vaultId);
        return ResponseEntity.ok(closed);
    }

    @GetMapping("/{vaultId}/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<VaultTransactionResponse>> getVaultTransactions(
            @PathVariable String vaultId,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching transactions for vault {} for customer ID {}", vaultId, principal != null ? principal.getUserId() : null);
        List<VaultTransactionResponse> txs = vaultService.getVaultTransactions(principal.getUserId(), vaultId);
        return ResponseEntity.ok(txs);
    }
}
