package com.group2.fse.orchestration_service.event;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.client.InternalApi;
import com.group2.fse.orchestration_service.saga.Stage;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes one transaction.stage.changed.v1 event per stage, keyed by transaction ID.
 * Callers publish only after the saga row is saved. Kafka delivers at least once and a
 * resumed saga republishes its last stage, so consumers treat (transactionId, stageNo)
 * as the identity of a stage.
 */
@Slf4j
@Component
public class StageEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;

    public StageEventPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
            @Value("${app.kafka.topics.stage-changed:transaction.stage.changed.v1}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    public void passed(UUID transactionId, String referenceNo, Stage stage, String detail) {
        publish(transactionId, referenceNo, stage, null, detail);
    }

    public void failed(UUID transactionId, String referenceNo, Stage stage, String failureCode, String detail) {
        publish(transactionId, referenceNo, stage, failureCode, detail);
    }

    private void publish(UUID transactionId, String referenceNo, Stage stage, String failureCode, String detail) {
        boolean failed = failureCode != null;
        Instant now = Instant.now();
        // Customer view (contract 2.7): stages 2 to 7 are PROCESSING, stage 8 onward is POSTED.
        String transactionStatus = failed ? "FAILED" : stage.no() >= Stage.POSTED.no() ? "POSTED" : "PROCESSING";

        StageChangedEvent event = new StageChangedEvent(
                "evt_" + UUID.randomUUID(),
                StageChangedEvent.EVENT_TYPE,
                now,
                "1.0",
                MDC.get(InternalApi.CORRELATION_MDC_KEY),
                new StageChangedEvent.Payload(transactionId, referenceNo, stage.no(), stage.name(),
                        failed ? "FAILED" : "PASSED", transactionStatus, failureCode, detail,
                        failed ? customerMessage(failureCode) : null, now));

        // ponytail: sent straight to Kafka, so a broker outage after the row is saved loses the
        // stage event. Move to an outbox table if the timeline must survive that.
        // The saga row is already saved, so a failure here is logged and never stops the saga.
        try {
            kafkaTemplate.send(topic, transactionId.toString(), jsonMapper.writeValueAsString(event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Stage event {} for transaction {} was not published: {}",
                                    stage, transactionId, ex.getMessage(), ex);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("Stage event {} for transaction {} was not published: {}",
                    stage, transactionId, e.getMessage(), e);
        }
    }

    /** A plain sentence for the customer; never a score or an internal reason. */
    private static String customerMessage(String failureCode) {
        return switch (failureCode) {
            case "INSUFFICIENT_FUNDS" -> "Your available balance is not enough for this transfer.";
            case "LIMIT_EXCEEDED" -> "This transfer is over your daily limit.";
            case "RISK_DECLINED" -> "This transfer could not be approved. Please contact your branch.";
            default -> "We could not complete this transfer. No money has left your account.";
        };
    }
}
