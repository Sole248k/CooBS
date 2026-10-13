package com.group2.fse.account_service.controller;

import com.group2.fse.account_service.dto.sharecapital.*;
import com.group2.fse.account_service.security.jwt.UserPrincipal;
import com.group2.fse.account_service.service.ShareCapitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts/share-capital")
@RequiredArgsConstructor
public class ShareCapitalController {

    private final ShareCapitalService shareCapitalService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ShareCapitalResponse> getMyShareCapital(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long customerId = resolveCustomerId(principal);
        log.info("Fetching share capital portfolio for customer ID {}", customerId);
        ShareCapitalResponse response = shareCapitalService.getMemberShareCapital(customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/top-up")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<ShareCapitalTopUpResponse> topUpShareCapital(
            @Valid @RequestBody ShareCapitalTopUpRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long customerId = resolveCustomerId(principal);
        log.info("Top-up share capital request of ₱{} for customer ID {}", request.getAmount(), customerId);
        ShareCapitalTopUpResponse response = shareCapitalService.topUpShareCapital(customerId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dividends")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<DividendPayoutResponse>> getDividendHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long customerId = resolveCustomerId(principal);
        log.info("Fetching dividend history for customer ID {}", customerId);
        List<DividendPayoutResponse> history = shareCapitalService.getDividendHistory(customerId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<ShareCapitalTransactionResponse>> getTransactions(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long customerId = resolveCustomerId(principal);
        log.info("Fetching share capital transaction audit for customer ID {}", customerId);
        List<ShareCapitalTransactionResponse> txs = shareCapitalService.getTransactions(customerId);
        return ResponseEntity.ok(txs);
    }

    private Long resolveCustomerId(UserPrincipal principal) {
        if (principal != null && principal.getUserId() != null) {
            return principal.getUserId();
        }
        return 1001L; // Fallback to primary demo member ID in development
    }
}
