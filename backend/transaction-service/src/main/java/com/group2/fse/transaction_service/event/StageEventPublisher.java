package com.group2.fse.transaction_service.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Task: Day 5 Event Propagation
 * Publishes transaction.stage.changed.v1 events for ingress stages (1-3)
 * as required by contract section 13 (H7).
 */
@Slf4j
@Component
public class StageEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public StageEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.stage-changed:transaction.stage.changed.v1}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishStage(UUID transactionId, String referenceNo, int stageNo, String stageName, String outcome, String detail) {
        Instant now = Instant.now();
        StageChangedEvent event = new StageChangedEvent(
                "evt_" + UUID.randomUUID(),
                StageChangedEvent.EVENT_TYPE,
                now,
                "1.0",
                null,
                new StageChangedEvent.Payload(
                        transactionId,
                        referenceNo,
                        stageNo,
                        stageName,
                        outcome,
                        "PROCESSING",
                        null,
                        detail,
                        null,
                        now
                )
        );

        try {
            kafkaTemplate.send(topic, transactionId.toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish stage changed event to Kafka topic [{}] for txnId={}, stage={}: {}",
                                    topic, transactionId, stageName, ex.getMessage());
                        } else {
                            log.debug("Published stage changed event to topic [{}] for txnId={}, stage={}",
                                    topic, transactionId, stageName);
                        }
                    });
        } catch (Exception ex) {
            log.error("Error dispatching stage changed event to Kafka for txnId={}: {}", transactionId, ex.getMessage(), ex);
        }
    }
}
