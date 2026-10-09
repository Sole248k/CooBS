package com.group2.fse.notification_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Task: FSE-601
 * Contract-compatible consumer DTO for account closure events.
 * Matches API_DESIGN_SPECIFICATION.md Section 6.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountClosureEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String version;
    private ClosurePayload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClosurePayload {
        private Long closureRequestId;
        private Long accountId;
        private String accountNumber;
        private Long customerId;
        private Long approvedBy;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal finalBalance;
    }
}
