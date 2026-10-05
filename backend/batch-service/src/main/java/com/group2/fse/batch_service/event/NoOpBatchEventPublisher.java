package com.group2.fse.batch_service.event;

import com.group2.fse.batch_service.dto.BatchStepCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback event publisher when Kafka is disabled (e.g. unit tests or local non-Kafka profiles).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "batch.kafka.enabled", havingValue = "false")
public class NoOpBatchEventPublisher implements BatchEventPublisher {

    @Override
    public void publishStepCompleted(BatchStepCompletedEvent event) {
        if (event != null) {
            log.debug("Kafka disabled: No-op step completed event for step {} in run #{}",
                    event.getStepName(), event.getBatchRunId());
        }
    }
}
