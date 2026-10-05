package com.group2.fse.customer_service.controller;

import com.group2.fse.customer_service.dto.*;
import com.group2.fse.customer_service.security.UserPrincipal;
import com.group2.fse.customer_service.service.BeneficiaryService;
import com.group2.fse.customer_service.service.Customer360Service;
import com.group2.fse.customer_service.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final BeneficiaryService beneficiaryService;
    private final Customer360Service customer360Service;

    // =========================================================================
    // C1 & C2: Customer Profile
    // =========================================================================

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerProfileResponse> getAuthenticatedCustomerProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching profile for authenticated customer ID {}", principal.getUserId());
        CustomerProfileResponse profile = customerService.getCustomerProfile(principal.getUserId());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<CustomerProfileResponse> getCustomerById(
            @PathVariable Long customerId) {
        log.info("Staff lookup for customer ID {}", customerId);
        CustomerProfileResponse profile = customerService.getCustomerById(customerId);
        return ResponseEntity.ok(profile);
    }

    // =========================================================================
    // C3: Customer 360 Aggregator
    // =========================================================================

    @GetMapping("/{customerId}/360")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<Customer360Response> getCustomer360(
            @PathVariable Long customerId,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isStaff = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TELLER") || a.getAuthority().equals("ROLE_ADMIN"));

        if (!isStaff && !principal.getUserId().equals(customerId)) {
            throw new AccessDeniedException("You can only access your own 360 profile");
        }

        log.info("Customer 360 request for customer ID {} by user {}", customerId, principal.getUserId());
        Customer360Response response = customer360Service.getCustomer360(customerId, isStaff);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // C4, C5, C6: Beneficiaries
    // =========================================================================

    @GetMapping("/me/beneficiaries")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<BeneficiaryDto>> getBeneficiaries(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching beneficiaries for customer ID {}", principal.getUserId());
        List<BeneficiaryDto> list = beneficiaryService.getBeneficiaries(principal.getUserId());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/me/beneficiaries")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<BeneficiaryDto> addBeneficiary(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateBeneficiaryRequest request) {
        log.info("Adding beneficiary for customer ID {}", principal.getUserId());
        BeneficiaryDto response = beneficiaryService.addBeneficiary(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/me/beneficiaries/{beneficiaryId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> deleteBeneficiary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long beneficiaryId) {
        log.info("Deleting beneficiary ID {} for customer ID {}", beneficiaryId, principal.getUserId());
        beneficiaryService.deleteBeneficiary(principal.getUserId(), beneficiaryId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // C8 - C12: KYC Workflow
    // =========================================================================

    @PostMapping("/kyc/submit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<KycRequestResponse> submitKyc(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody KycSubmitRequest request) {
        log.info("Customer ID {} submitting initial KYC verification", principal.getUserId());
        KycRequestResponse response = customerService.submitKyc(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/kyc/update-request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<KycRequestResponse> submitKycUpdateRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody KycUpdateRequestDto request) {
        log.info("Customer ID {} submitting KYC update request", principal.getUserId());
        KycRequestResponse response = customerService.submitKycUpdateRequest(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/kyc/update-requests")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<PageResponse<KycRequestResponse>> getPendingKycUpdateRequests(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<KycRequestResponse> response = customerService.getPendingKycUpdateRequests(status, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/kyc/update-requests/{id}/approve", "/kyc/update-request/{id}/approve"})
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<KycRequestResponse> approveKycUpdateRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Staff ID {} approving KYC update request #{}", principal.getUserId(), id);
        KycRequestResponse response = customerService.approveKycUpdateRequest(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/kyc/update-requests/{id}/reject", "/kyc/update-request/{id}/reject"})
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<KycRequestResponse> rejectKycUpdateRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody KycRejectRequest request) {
        log.info("Staff ID {} rejecting KYC update request #{}", principal.getUserId(), id);
        KycRequestResponse response = customerService.rejectKycUpdateRequest(id, principal.getUserId(), request.getRejectionReason());
        return ResponseEntity.ok(response);
    }
}
