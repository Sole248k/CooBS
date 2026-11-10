package com.group2.fse.notification_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Task: FSE-601
 * Resilient consumer DTO for account closure events.
 * Supports both envelope contract events (API_DESIGN_SPECIFICATION.md Section 6.2)
 * and flat domain events published by account-service.
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

    // Direct root fields for flat events from account-service
    @JsonProperty("closureRequestId")
    private Long rootClosureRequestId;

    @JsonProperty("accountId")
    private Long rootAccountId;

    @JsonProperty("accountNumber")
    private String rootAccountNumber;

    @JsonProperty("customerId")
    private Long rootCustomerId;

    @JsonProperty("approvedBy")
    private Long rootApprovedBy;

    @JsonProperty("finalBalance")
    private BigDecimal rootFinalBalance;

    @JsonSetter("timestamp")
    public void setTimestampFlexible(Object ts) {
        if (ts instanceof String s) {
            try {
                this.timestamp = Instant.parse(s);
            } catch (Exception e) {
                try {
                    this.timestamp = LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
                } catch (Exception ex) {
                    this.timestamp = Instant.now();
                }
            }
        } else if (ts instanceof Number n) {
            this.timestamp = Instant.ofEpochMilli(n.longValue());
        } else {
            this.timestamp = Instant.now();
        }
    }

    public ClosurePayload getPayload() {
        if (this.payload != null) {
            return this.payload;
        }
        if (this.rootCustomerId != null || this.rootAccountId != null || this.rootClosureRequestId != null) {
            return ClosurePayload.builder()
                    .closureRequestId(this.rootClosureRequestId)
                    .accountId(this.rootAccountId)
                    .accountNumber(this.rootAccountNumber)
                    .customerId(this.rootCustomerId)
                    .approvedBy(this.rootApprovedBy)
                    .finalBalance(this.rootFinalBalance != null ? this.rootFinalBalance : BigDecimal.ZERO)
                    .build();
        }
        return null;
    }

    public String getEventId() {
        if (this.eventId != null && !this.eventId.isBlank()) {
            return this.eventId;
        }
        if (this.rootClosureRequestId != null) {
            return "EVT-CLOSURE-" + this.rootClosureRequestId;
        }
        if (this.payload != null && this.payload.getClosureRequestId() != null) {
            return "EVT-CLOSURE-" + this.payload.getClosureRequestId();
        }
        return "EVT-CLOSURE-" + (this.rootAccountId != null ? this.rootAccountId : System.currentTimeMillis());
    }

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
