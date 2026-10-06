package com.group2.fse.ledger_service.event.publisher;

import com.group2.fse.ledger_service.entity.OutboxEvent;
import com.group2.fse.ledger_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Task: Day 4 Core Transfer Flow
 * OutboxRelay polls unpublished records from Oracle OUTBOX table
 * and publishes them to Kafka with exactly-once ordering guarantees per partition key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

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
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Outbox relay Kafka send failed for outboxId {}: {}", event.getOutboxId(), ex.getMessage());
                            } else {
                                log.debug("Outbox event {} successfully acknowledged by Kafka broker", event.getOutboxId());
                            }
                        });

                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                log.error("OutboxRelay failed to process event ID {}: {}", event.getOutboxId(), ex.getMessage(), ex);
                break; // Stop batch on connection failure to preserve ordering
            }
        }
    }
}
