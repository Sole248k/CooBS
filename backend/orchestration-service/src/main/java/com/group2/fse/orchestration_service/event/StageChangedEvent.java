package com.group2.fse.orchestration_service.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** transaction.stage.changed.v1 in the shared event envelope (contract section 13, H7). */
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
