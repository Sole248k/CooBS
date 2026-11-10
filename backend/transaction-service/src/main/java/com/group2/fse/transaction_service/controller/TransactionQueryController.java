package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.TransactionDetailResponseDto;
import com.group2.fse.transaction_service.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for transaction detail retrieval (T2).
 * Maps GET /api/v1/transactions/{transactionId}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionQueryController {

    private final TimelineService timelineService;

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionDetailResponseDto> getTransactionDetail(
            @PathVariable("transactionId") String transactionId) {
        log.info("Fetching transaction details for identifier={}", transactionId);
        TransactionDetailResponseDto response = timelineService.getTransactionDetail(transactionId);
        return ResponseEntity.ok(response);
    }
}
