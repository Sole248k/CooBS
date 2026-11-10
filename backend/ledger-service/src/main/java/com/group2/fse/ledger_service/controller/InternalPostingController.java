package com.group2.fse.ledger_service.controller;

import com.group2.fse.ledger_service.dto.PostingsRequestDto;
import com.group2.fse.ledger_service.dto.PostingsResponseDto;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.service.PostingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * L8: Internal ledger posting endpoint handling inter-bank credit settlements (FIN-407 / H22)
 * and multi-posting batches from batch-service as specified in FUTURE_INTEGRATED_API_CONTRACT.md section 10.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/ledger/postings")
@RequiredArgsConstructor
public class InternalPostingController {

    private final PostingsService postingsService;

    /**
     * A posting is recognised by its referenceNo, so a repeated request is safe with or
     * without the Idempotency-Key header: what is already in the journal is reported POSTED
     * and not posted again.
     */
    @PostMapping
    public ResponseEntity<PostingsResponseDto> executePostings(
            @Valid @RequestBody PostingsRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        log.info("L8 postings request received: batchRunId={}, stepName={}, txId={}, postingsCount={}, idempKey={}",
                request.getBatchRunId(), request.getStepName(), request.getTransactionId(),
                request.getPostings().size(), idempotencyKey);

        boolean batchCall = request.getBatchRunId() != null
                && request.getStepName() != null && !request.getStepName().isBlank();
        if (!batchCall && request.getTransactionId() == null) {
            throw new InvalidTransactionException(
                    "Send batchRunId and stepName for a batch step, or transactionId for an inter-bank credit.");
        }

        PostingsResponseDto response = postingsService.post(request, extractClientIp(httpRequest));

        log.info("L8 postings done: batchRunId={}, txId={}, posted={}, failed={}",
                request.getBatchRunId(), request.getTransactionId(), response.getPosted(), response.getFailed());
        return ResponseEntity.ok(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
