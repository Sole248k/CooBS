package com.group2.fse.ledger_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * L8 request. A batch step sends batchRunId and stepName; an inbound inter-bank credit sends
 * transactionId and one posting.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostingsRequestDto {

    private Long batchRunId;
    private String stepName;
    private UUID transactionId;

    // The person who caused the postings, when there is one. A partner bank has none.
    private Long actorId;

    @NotEmpty(message = "postings must hold at least one posting")
    @Size(max = 500, message = "postings must not exceed 500 in one request")
    @Valid
    private List<PostingItem> postings;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostingItem {

        // 33, not 36: each leg is stored as the reference plus -D or -C and
        // TRANSACTION.reference_no is VARCHAR2(36). A longer reference would be cut, and two
        // references that differ only after the cut would be taken for the same posting.
        @NotBlank(message = "referenceNo is required")
        @Size(max = 33, message = "referenceNo must not exceed 33 characters")
        private String referenceNo;

        @NotNull(message = "debitAccountId is required")
        private Long debitAccountId;

        @NotNull(message = "creditAccountId is required")
        private Long creditAccountId;

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be strictly positive")
        @Digits(integer = 14, fraction = 4, message = "amount must have at most 4 decimal places")
        private BigDecimal amount;

        @Size(max = 255, message = "remarks must not exceed 255 characters")
        private String remarks;
    }
}
