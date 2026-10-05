package com.group2.fse.batch_service.event;

import com.group2.fse.batch_service.dto.BatchStepCompletedEvent;

/**
 * Publisher contract for EOD batch domain events.
 */
public interface BatchEventPublisher {

    /**
     * Publishes step completion event to Kafka topic 'batch.eod.step.completed.v1'.
     */
    void publishStepCompleted(BatchStepCompletedEvent event);
}
