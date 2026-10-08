package com.group2.fse.ledger_service.event.publisher;

import com.group2.fse.ledger_service.entity.OutboxEvent;
import com.group2.fse.ledger_service.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Task: Day 4 Core Transfer Flow
 * OutboxRelay polls unpublished records from Oracle OUTBOX table
 * and publishes them to Kafka with exactly-once ordering guarantees per partition key.
 */
@Slf4j
@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       KafkaTemplate<String, String> outboxKafkaTemplate,
                       @Value("${ledger.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxKafkaTemplate = outboxKafkaTemplate;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${ledger.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingOutboxEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublishedEvents(PageRequest.of(0, 50));
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox event(s) to publish", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // Wait for the broker before stamping the row: a row marked published is never
                // sent again, so stamping on an unacknowledged send loses the event for good.
                outboxKafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);

                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.debug("Outbox event {} acknowledged by Kafka broker", event.getOutboxId());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("OutboxRelay could not publish event ID {}; it stays pending: {}",
                        event.getOutboxId(), ex.getMessage(), ex);
                break; // Stop the batch so that later events do not overtake this one
            }
        }
    }
}
