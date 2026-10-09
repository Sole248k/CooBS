package com.group2.fse.account_service.controller;

import com.group2.fse.account_service.dto.*;
import com.group2.fse.account_service.security.jwt.UserPrincipal;
import com.group2.fse.account_service.service.AccountClosureService;
import com.group2.fse.account_service.service.AccountFlagService;
import com.group2.fse.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountClosureService accountClosureService;
    private final AccountFlagService accountFlagService;

    // =========================================================================
    // 1. Account Lifecycle
    // =========================================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<AccountResponse> openAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        log.info("Opening new {} account for customer ID {}", request.getAccountType(), request.getCustomerId());
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        log.info("Staff requesting all active bank accounts");
        List<AccountResponse> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/my-accounts")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isStaff = principal != null && principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TELLER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (isStaff) {
            log.info("Staff user {} fetching all bank accounts via /my-accounts", principal.getUsername());
            return ResponseEntity.ok(accountService.getAllAccounts());
        }
        log.info("Fetching accounts for customer ID {}", principal != null ? principal.getUserId() : null);
        List<AccountResponse> accounts = accountService.getCustomerAccounts(principal.getUserId());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isStaff = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TELLER") || a.getAuthority().equals("ROLE_ADMIN"));
        AccountResponse response = accountService.getAccountById(accountId, principal.getUserId(), isStaff);
        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/{accountId}/status", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountStatusUpdateRequest request) {
        log.info("Updating status for account ID {} to {}", accountId, request.getStatus());
        AccountResponse response = accountService.updateAccountStatus(accountId, request.getStatus());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 2. Account Closure Workflow
    // =========================================================================

    @PostMapping("/{accountId}/closure-request")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ClosureRequestResponse> submitClosureRequest(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) AccountClosureRequestDto request) {
        boolean isStaff = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TELLER") || a.getAuthority().equals("ROLE_ADMIN"));

        Long customerId = principal.getUserId();
        if (isStaff) {
            AccountResponse acc = accountService.getAccountById(accountId, principal.getUserId(), true);
            customerId = acc.getCustomerId();
            log.info("Staff member {} submitting closure request on behalf of customer ID {} for account ID {}",
                    principal.getUsername(), customerId, accountId);
        } else {
            log.info("Customer ID {} submitting closure request for account ID {}", customerId, accountId);
        }

        ClosureRequestResponse response = accountClosureService.submitClosureRequest(accountId, customerId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{accountId}/closure-request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ClosureRequestResponse> getClosureRequest(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClosureRequestResponse response = accountClosureService.getClosureRequestForAccount(accountId, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/closure-requests")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<PageResponse<ClosureRequestResponse>> getPendingClosureRequests(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<ClosureRequestResponse> response = accountClosureService.getPendingClosureRequests(status, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/closure-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClosureRequestResponse> approveClosureRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Admin ID {} approving closure request #{}", principal.getUserId(), id);
        ClosureRequestResponse response = accountClosureService.approveClosureRequest(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/closure-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClosureRequestResponse> rejectClosureRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RejectClosureRequestDto request) {
        log.info("Admin ID {} rejecting closure request #{}", principal.getUserId(), id);
        ClosureRequestResponse response = accountClosureService.rejectClosureRequest(id, principal.getUserId(), request.getRejectionReason());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 3. Administrative & Risk Holds
    // =========================================================================

    @PostMapping("/{accountId}/flags")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<AccountFlagResponse> addFlag(
            @PathVariable Long accountId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AccountFlagRequest request) {
        log.info("Imposing hold on account ID {} by staff user ID {}", accountId, principal.getUserId());
        AccountFlagResponse response = accountFlagService.addFlag(accountId, request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{accountId}/flags/{flagId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountFlagResponse> removeFlag(
            @PathVariable Long accountId,
            @PathVariable Long flagId,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Admin ID {} lifting hold #{} on account ID {}", principal.getUserId(), flagId, accountId);
        AccountFlagResponse response = accountFlagService.removeFlag(accountId, flagId, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/flags")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<List<AccountFlagResponse>> getFlags(
            @PathVariable Long accountId) {
        List<AccountFlagResponse> flags = accountFlagService.getFlagsForAccount(accountId);
        return ResponseEntity.ok(flags);
    }
}
