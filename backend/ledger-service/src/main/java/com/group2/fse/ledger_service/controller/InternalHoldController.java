package com.group2.fse.ledger_service.controller;

import com.group2.fse.ledger_service.dto.*;
import com.group2.fse.ledger_service.security.jwt.UserPrincipal;
import com.group2.fse.ledger_service.service.HoldService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Internal Two-Phase Commitment Ledger Hold Controller (L3 - L6).
 * Endpoints: /internal/v1/ledger/holds
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/ledger/holds")
@RequiredArgsConstructor
public class InternalHoldController {

    private final HoldService holdService;

    /**
     * L3: Reserve balance hold.
     */
    @PostMapping
    public ResponseEntity<PlaceHoldResponseDto> placeHold(
            @Valid @RequestBody PlaceHoldRequestDto requestDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        log.info("L3 place hold request received: ref={}, accountId={}, amount={}, idempKey={}",
                requestDto.getReferenceNo(), requestDto.getAccountId(), requestDto.getAmount(), idempotencyKey);

        if (requestDto.getActorId() == null) {
            requestDto.setActorId(extractActorId(authentication));
        }

        PlaceHoldResponseDto response = holdService.placeHold(requestDto, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * L4: Capture reserved hold and finalize transfer posting.
     */
    @PostMapping("/{holdId}/capture")
    public ResponseEntity<CaptureHoldResponseDto> captureHold(
            @PathVariable("holdId") Long holdId,
            @Valid @RequestBody CaptureHoldRequestDto requestDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {

        log.info("L4 capture hold request received: holdId={}, dstAccountId={}, idempKey={}",
                holdId, requestDto.getDestinationAccountId(), idempotencyKey);

        if (requestDto.getActorId() == null) {
            requestDto.setActorId(extractActorId(authentication));
        }
        if (requestDto.getClientIp() == null || requestDto.getClientIp().isBlank()) {
            requestDto.setClientIp(extractClientIp(request));
        }

        CaptureHoldResponseDto response = holdService.captureHold(holdId, requestDto, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * L5: Release/cancel reserved hold.
     */
    @PostMapping("/{holdId}/release")
    public ResponseEntity<ReleaseHoldResponseDto> releaseHold(
            @PathVariable("holdId") Long holdId,
            @RequestBody(required = false) ReleaseHoldRequestDto requestDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("L5 release hold request received: holdId={}, idempKey={}", holdId, idempotencyKey);

        if (requestDto == null) {
            requestDto = ReleaseHoldRequestDto.builder().reason("MANUAL_RELEASE").build();
        }

        ReleaseHoldResponseDto response = holdService.releaseHold(holdId, requestDto, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * L6: Query hold details.
     */
    @GetMapping("/{holdId}")
    public ResponseEntity<HoldDetailDto> getHold(@PathVariable("holdId") Long holdId) {
        log.info("L6 get hold details request received: holdId={}", holdId);
        HoldDetailDto detail = holdService.getHold(holdId);
        return ResponseEntity.ok(detail);
    }

    private Long extractActorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
