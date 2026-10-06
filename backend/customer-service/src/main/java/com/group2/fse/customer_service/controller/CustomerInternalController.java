package com.group2.fse.customer_service.controller;

import com.group2.fse.customer_service.dto.RiskProfileResponse;
import com.group2.fse.customer_service.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/v1/customers")
@RequiredArgsConstructor
public class CustomerInternalController {

    private final CustomerService customerService;

    @GetMapping("/{customerId}/risk-profile")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<RiskProfileResponse> getRiskProfile(@PathVariable Long customerId) {
        log.info("Internal saga risk profile lookup for customer ID {}", customerId);
        RiskProfileResponse response = customerService.getRiskProfile(customerId);
        return ResponseEntity.ok(response);
    }
}
