package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.dto.TransferRequestDto;
import com.group2.fse.transaction_service.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for transaction transfer ingress (T1).
 * Maps POST /api/v1/transactions/transfers returning immediate 202 Accepted.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/transfers")
    public ResponseEntity<TransferInitiateResponseDto> initiateTransfer(
            @Valid @RequestBody TransferRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        log.info("Received transfer ingress request: srcAccount={}, dstAccount={}, amount={}, refKey={}",
                request.getSourceAccountId(), request.getDestinationAccountNumber(), request.getAmount(), idempotencyKey);

        TransferInitiateResponseDto response = transferService.initiateTransfer(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
