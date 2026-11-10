package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.TimelineResponseDto;
import com.group2.fse.transaction_service.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for transaction real-time timeline tracking (T3).
 * Maps GET /api/v1/transactions/{transactionId}/timeline
 * Returns 3-stage customer view or 11-stage staff view.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    @GetMapping("/{transactionId}/timeline")
    public ResponseEntity<TimelineResponseDto> getTimeline(
            @PathVariable("transactionId") String transactionId,
            @RequestParam(value = "view", required = false) String view,
            Authentication authentication) {
        log.info("Fetching timeline for transactionId={}, view={}", transactionId, view);
        TimelineResponseDto response = timelineService.getTimeline(transactionId, view, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{trackingRef}/status")
    public ResponseEntity<com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto> getExecutionStatus(
            @PathVariable("trackingRef") String trackingRef) {
        log.info("Fetching execution status for trackingRef={}", trackingRef);
        com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto response = timelineService.getExecutionStatus(trackingRef);
        return ResponseEntity.ok(response);
    }
}
