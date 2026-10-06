package com.group2.fse.ledger_service.reversal.controller;

import com.group2.fse.ledger_service.reversal.dto.ReversalRequestDto;
import com.group2.fse.ledger_service.reversal.dto.ReversalResponseDto;
import com.group2.fse.ledger_service.reversal.service.ReversalService;
import com.group2.fse.ledger_service.security.jwt.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing Core Ledger Reversal endpoints.
 * Implements Handshake H3 L7 contract: POST /internal/v1/ledger/reversals
 * and public alias POST /api/v1/ledger/reversals.
 */
@Slf4j
@RestController
@RequestMapping({"/internal/v1/ledger/reversals", "/api/v1/ledger/reversals"})
@RequiredArgsConstructor
public class ReversalController {

    private final ReversalService reversalService;

    @PostMapping
    public ResponseEntity<ReversalResponseDto> reverse(
            @Valid @RequestBody ReversalRequestDto requestDto,
            Authentication authentication,
            HttpServletRequest request) {

        Long actorId = extractActorId(authentication);
        String clientIp = extractClientIp(request);

        log.info("REST ledger reversal requested: originalRef={}, reversalRef={}, actorId={}, ip={}",
                requestDto.getOriginalReferenceNo(), requestDto.getReversalReferenceNo(), actorId, clientIp);

        ReversalResponseDto response = reversalService.processReversal(requestDto, actorId, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
