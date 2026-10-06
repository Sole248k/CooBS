package com.group2.fse.transaction_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared event envelope for topic transaction.stage.changed.v1 (contract section 13, H7).
 */
public record StageChangedEvent(
        String eventId,
        String eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        String version,
        String correlationId,
        Payload payload) {

    public static final String EVENT_TYPE = "TRANSACTION_STAGE_CHANGED";

    public record Payload(
            UUID transactionId,
            String referenceNo,
            int stageNo,
            String stageName,
            String outcome,
            String transactionStatus,
            String failureCode,
            String detail,
            String customerMessage,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
    }
}
