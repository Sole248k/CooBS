package com.group2.fse.notification_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Task: Day 5 Event Propagation
 * Domain Event published to notification.dispatched.v1 (contract section 13).
 * Consumed by orchestration-service to complete stages 10 (NOTIFICATION)
 * and 11 (RECONCILIATION).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDispatchedEvent {

    @Builder.Default
    private String eventId = "evt_" + java.util.UUID.randomUUID();

    @Builder.Default
    private String eventType = "NOTIFICATION_DISPATCHED";

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp = Instant.now();

    @Builder.Default
    private String version = "1.0";

    private DispatchedPayload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchedPayload {
        private String transactionId;
        private String referenceNo;
        private String sourceEventId;
        private List<Long> customerIds;
        private String channel;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Instant dispatchedAt;
    }
}
