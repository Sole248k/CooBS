package com.group2.fse.batch_service.event;

import com.group2.fse.batch_service.dto.BatchStepCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Production Kafka event publisher for EOD batch step events.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "batch.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class KafkaBatchEventPublisher implements BatchEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${batch.kafka.topics.step-completed:batch.eod.step.completed.v1}")
    private String stepCompletedTopic;

    @Override
    public void publishStepCompleted(BatchStepCompletedEvent event) {
        if (event == null) return;
        String partitionKey = event.getBatchRunId() != null ? event.getBatchRunId().toString() : "0";
        try {
            log.info("[BatchRun #{}] Emitting {} event to Kafka topic '{}'",
                    event.getBatchRunId(), event.getStepName(), stepCompletedTopic);
            kafkaTemplate.send(stepCompletedTopic, partitionKey, event);
        } catch (Exception ex) {
            log.warn("[BatchRun #{}] Failed to emit event to Kafka topic '{}': {}",
                    event.getBatchRunId(), stepCompletedTopic, ex.getMessage());
        }
    }
}
