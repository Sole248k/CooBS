package com.group2.fse.ledger_service.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.group2.fse.ledger_service.dto.TransferRequestDto;
import com.group2.fse.ledger_service.dto.TransferResponseDto;
import com.group2.fse.ledger_service.service.AccountBalanceService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * L8: Internal Atomic Postings Controller (/internal/v1/ledger/postings).
 * Supports inbound inter-bank credits from orchestration-service (O4)
 * and multi-posting batches from batch-service as specified in FUTURE_INTEGRATED_API_CONTRACT.md section 10.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/ledger/postings")
@RequiredArgsConstructor
public class InternalPostingController {

    private final AccountBalanceService accountBalanceService;

    @PostMapping
    public ResponseEntity<PostingsBatchResponse> executePostings(
            @RequestBody PostingsBatchRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("L8 postings request received: batchRunId={}, stepName={}, txId={}, postingsCount={}",
                request.getBatchRunId(), request.getStepName(), request.getTransactionId(),
                request.getPostings() != null ? request.getPostings().size() : 0);

        List<PostingItemResult> results = new ArrayList<>();
        int postedCount = 0;
        int failedCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        if (request.getPostings() != null) {
            for (PostingItem posting : request.getPostings()) {
                try {
                    TransferRequestDto transferDto = TransferRequestDto.builder()
                            .sourceAccountId(posting.getDebitAccountId())
                            .destinationAccountId(posting.getCreditAccountId())
                            .amount(posting.getAmount())
                            .referenceNo(posting.getReferenceNo())
                            .remarks(posting.getRemarks() != null ? posting.getRemarks() : "Inter-bank credit posting")
                            .build();

                    TransferResponseDto responseDto = accountBalanceService.executeTransfer(transferDto, 0L, "127.0.0.1");
                    postedCount++;
                    totalAmount = totalAmount.add(posting.getAmount());
                    results.add(new PostingItemResult(posting.getReferenceNo(), "POSTED", null));
                    log.info("Successfully posted L8 entry ref={}: transferRef={}", posting.getReferenceNo(), responseDto.getTransferReference());
                } catch (Exception ex) {
                    failedCount++;
                    String errorCode = ex.getMessage() != null ? ex.getMessage() : "LEDGER_POSTING_FAILED";
                    results.add(new PostingItemResult(posting.getReferenceNo(), "FAILED", errorCode));
                    log.error("Failed to post L8 entry ref={}: {}", posting.getReferenceNo(), ex.getMessage());
                }
            }
        }

        PostingsBatchResponse response = PostingsBatchResponse.builder()
                .batchRunId(request.getBatchRunId())
                .stepName(request.getStepName())
                .posted(postedCount)
                .failed(failedCount)
                .totalAmount(totalAmount)
                .results(results)
                .build();

        return ResponseEntity.ok(response);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostingsBatchRequest {
        private Long batchRunId;
        private String stepName;
        private UUID transactionId;
        private List<PostingItem> postings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostingItem {
        private String referenceNo;
        private Long debitAccountId;
        private Long creditAccountId;
        private BigDecimal amount;
        private String remarks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PostingsBatchResponse {
        private Long batchRunId;
        private String stepName;
        private int posted;
        private int failed;
        private BigDecimal totalAmount;
        private List<PostingItemResult> results;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PostingItemResult {
        private String referenceNo;
        private String status;
        private String errorCode;
    }
}
