package com.group2.fse.mobile_bff.controller;

import com.group2.fse.mobile_bff.dto.MobileDashboardResponse;
import com.group2.fse.mobile_bff.dto.MobileRemittanceRequest;
import com.group2.fse.mobile_bff.dto.MobileRemittanceResponse;
import com.group2.fse.mobile_bff.dto.MobileTimelineResponse;
import com.group2.fse.mobile_bff.exception.ApiException;
import com.group2.fse.mobile_bff.security.UserPrincipal;
import com.group2.fse.mobile_bff.service.MobileBffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/mobile")
@RequiredArgsConstructor
public class MobileBffController {

    private final MobileBffService mobileBffService;

    /**
     * M1: Mobile Composite Dashboard Aggregator (< 150ms target).
     * Concurrently aggregates member profile, primary account, balance, unread alerts, and recent transactions.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ROLE_CUSTOMER')")
    public ResponseEntity<MobileDashboardResponse> getDashboard(
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        Long customerId = resolveCustomerId(authentication);
        String authHeader = resolveAuthorization(authorization, authentication);
        log.info("Received M1 Dashboard request for Customer [{}] (correlationId={})", customerId, correlationId);

        MobileDashboardResponse response = mobileBffService.getDashboard(customerId, correlationId, authHeader);
        return ResponseEntity.ok(response);
    }

    /**
     * M2: 3-Stage Milestone Stepper Timeline Adapter.
     * Translates internal microservice saga states into member milestones (SUBMITTED, IN_REVIEW, COMPLETED).
     */
    @GetMapping("/transactions/{trackingReference}/timeline")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ROLE_CUSTOMER')")
    public ResponseEntity<MobileTimelineResponse> getTimeline(
            @PathVariable("trackingReference") String trackingReference,
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        Long customerId = resolveCustomerId(authentication);
        String authHeader = resolveAuthorization(authorization, authentication);
        log.info("Received M2 Timeline request for reference [{}] by customer [{}] (correlationId={})",
                trackingReference, customerId, correlationId);

        MobileTimelineResponse response = mobileBffService.getTimeline(trackingReference, customerId, correlationId, authHeader);
        return ResponseEntity.ok(response);
    }

    /**
     * M3: Mobile Remittance Submission Intake.
     * Validates client DTO, resolves account details, and dispatches to transaction-service returning 202 Accepted.
     */
    @PostMapping("/transactions/remittance")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ROLE_CUSTOMER')")
    public ResponseEntity<MobileRemittanceResponse> submitRemittance(
            @Valid @RequestBody MobileRemittanceRequest request,
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing mandatory Idempotency-Key header");
        }

        Long customerId = resolveCustomerId(authentication);
        String authHeader = resolveAuthorization(authorization, authentication);
        log.info("Received M3 Remittance request for Customer [{}] (idempotencyKey={}, correlationId={})",
                customerId, idempotencyKey, correlationId);

        MobileRemittanceResponse response = mobileBffService.submitRemittance(
                request, idempotencyKey, customerId, correlationId, authHeader);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private String resolveAuthorization(String authorization, Authentication authentication) {
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }
        if (authentication != null && authentication.getCredentials() != null) {
            String cred = authentication.getCredentials().toString();
            if (!cred.isBlank()) {
                return cred.startsWith("Bearer ") ? cred : "Bearer " + cred;
            }
        }
        return null;
    }

    private Long resolveCustomerId(Authentication authentication) {
        if (authentication == null) {
            authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        }
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated customer");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal up && up.getUserId() != null && up.getUserId() > 0) {
            return up.getUserId();
        }
        try {
            long parsed = Long.parseLong(authentication.getName());
            if (parsed > 0) {
                return parsed;
            }
        } catch (Exception ignored) {
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Unable to resolve customer identity from token");
    }
}
