package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.ReversalRequestDto;
import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.service.ReversalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T5: a Teller or Admin asks for a posted transfer to be reversed in full. The ledger does the
 * reversing, through the reversal saga (O2); this endpoint records the request and answers 202.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class ReversalController {

    private final ReversalService reversalService;

    @PostMapping("/{transactionId}/reversal")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<TransferInitiateResponseDto> requestReversal(
            @PathVariable String transactionId,
            @Valid @RequestBody ReversalRequestDto request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("Received reversal request for transaction {}", transactionId);

        TransferInitiateResponseDto response =
                reversalService.requestReversal(transactionId, request.getReason(), idempotencyKey);
        // A repeat of the same Idempotency-Key returns the same body with 200, as T1 does.
        return ResponseEntity.status(response.isReplayed() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(response);
    }
}
