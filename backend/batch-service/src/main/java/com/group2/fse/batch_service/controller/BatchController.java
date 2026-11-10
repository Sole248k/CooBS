package com.group2.fse.batch_service.controller;

import com.group2.fse.batch_service.dto.BatchRunDetailsResponseDto;
import com.group2.fse.batch_service.dto.EodTriggerRequestDto;
import com.group2.fse.batch_service.dto.EodTriggerResponseDto;
import com.group2.fse.batch_service.security.UserPrincipal;
import com.group2.fse.batch_service.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller exposing B1, B2, and B3 EOD Batch Operations according to Section 11 of the API Contract.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    /**
     * B1: Trigger End-of-Day Batch Execution.
     * Roles: ADMIN
     */
    @PostMapping("/eod/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EodTriggerResponseDto> triggerEod(
            @Valid @RequestBody(required = false) EodTriggerRequestDto request,
            Authentication authentication) {
        Long triggeredBy = extractActorId(authentication);
        log.info("REST request to trigger EOD batch received from actorId={}", triggeredBy);
        EodTriggerResponseDto response = batchService.triggerEod(request, triggeredBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * B2: Get Specific EOD Batch Run by ID.
     * Roles: ADMIN, TELLER
     */
    @GetMapping("/eod/runs/{batchRunId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<BatchRunDetailsResponseDto> getBatchRun(@PathVariable Long batchRunId) {
        log.info("REST request to get batch run details for batchRunId={}", batchRunId);
        BatchRunDetailsResponseDto response = batchService.getBatchRun(batchRunId);
        return ResponseEntity.ok(response);
    }

    /**
     * B3: List All EOD Batch Runs.
     * Roles: ADMIN, TELLER
     */
    @GetMapping("/eod/runs")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<List<BatchRunDetailsResponseDto>> listBatchRuns() {
        log.info("REST request to list all batch runs");
        List<BatchRunDetailsResponseDto> response = batchService.listBatchRuns();
        return ResponseEntity.ok(response);
    }

    private Long extractActorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return 1L; // Fallback default admin id if running without principal or in test harness
    }
}
