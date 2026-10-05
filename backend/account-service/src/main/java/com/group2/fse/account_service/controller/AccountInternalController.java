package com.group2.fse.account_service.controller;

import com.group2.fse.account_service.dto.AccountResponse;
import com.group2.fse.account_service.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class AccountInternalController {

    private final AccountService accountService;

    @GetMapping("/customers/{customerId}/accounts")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<List<AccountResponse>> getAccountsByCustomerId(@PathVariable Long customerId) {
        log.info("Internal accounts lookup for customer ID {}", customerId);
        List<AccountResponse> accounts = accountService.getCustomerAccounts(customerId);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<AccountResponse> getAccountByIdInternal(@PathVariable Long accountId) {
        log.info("Internal account lookup for account ID {}", accountId);
        AccountResponse response = accountService.getAccountById(accountId, null, true);
        return ResponseEntity.ok(response);
    }
}
