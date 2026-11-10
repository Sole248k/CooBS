package com.group2.fse.ledger_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * L8 response. Every posting has its own outcome, so the answer is 200 even when some were
 * refused: the caller reads {@code failed} and each result's {@code errorCode}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostingsResponseDto {

    public static final String POSTED = "POSTED";
    public static final String FAILED = "FAILED";

    private Long batchRunId;
    private String stepName;
    private UUID transactionId;
    private int posted;
    private int failed;

    // The sum of the postings that are POSTED. Money is a string in this contract.
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalAmount;

    private List<PostingResult> results;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostingResult {
        private String referenceNo;
        private String status;
        // Present on every result, null when POSTED, as in the contract example.
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String errorCode;
    }
}
