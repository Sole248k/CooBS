package com.group2.fse.transaction_service.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.event.StageChangedEvent;
import com.group2.fse.transaction_service.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction.stage.changed.v1 events and updates stage history
 * and transaction lifecycle status in transaction_db.
 *
 * The record arrives as a plain JSON string and is mapped here. Orchestration-service publishes
 * strings with no type headers, so a typed JSON deserializer on the consumer rejects every record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageEventListener {

    private final TimelineService timelineService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.stage-changed:transaction.stage.changed.v1}",
            groupId = "${spring.kafka.consumer.group-id:transaction-service-group}"
    )
    public void onStageChanged(String message) {
        StageChangedEvent event = read(message);
        if (event == null || event.payload() == null) {
            log.warn("Discarding null stage changed event or payload");
            return;
        }

        StageChangedEvent.Payload p = event.payload();
        log.info("Processing stage changed event for txn={}: stageNo={}, stageName={}, outcome={}, status={}",
                p.transactionId(), p.stageNo(), p.stageName(), p.outcome(), p.transactionStatus());

        timelineService.recordStage(
                p.transactionId(),
                p.stageNo(),
                p.stageName(),
                p.outcome(),
                p.detail(),
                p.occurredAt(),
                p.transactionStatus(),
                p.failureCode()
        );
    }

    /** A record that cannot be read will not become readable on a retry, so it is logged and skipped. */
    private StageChangedEvent read(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readerFor(StageChangedEvent.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(message);
        } catch (JsonProcessingException e) {
            log.error("Discarding unreadable stage changed event: {}", e.getOriginalMessage());
            return null;
        }
    }
}
