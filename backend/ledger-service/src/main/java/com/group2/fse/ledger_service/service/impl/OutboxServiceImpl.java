package com.group2.fse.ledger_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.ledger_service.entity.OutboxEvent;
import com.group2.fse.ledger_service.repository.OutboxEventRepository;
import com.group2.fse.ledger_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Task: Day 4 Core Transfer Flow
 * OutboxServiceImpl saves outbox domain events to the Oracle OUTBOX table
 * within the active transaction boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent appendEvent(String topic, String eventKey, Object payload) {
        try {
            String jsonPayload;
            if (payload instanceof String str) {
                jsonPayload = str;
            } else {
                jsonPayload = objectMapper.writeValueAsString(payload);
            }

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .topic(topic)
                    .eventKey(eventKey != null ? eventKey : "0")
                    .payload(jsonPayload)
                    .createdAt(LocalDateTime.now())
                    .build();

            OutboxEvent saved = outboxEventRepository.save(outboxEvent);
            log.info("Appended outbox event ID {} for topic [{}] with key [{}]", saved.getOutboxId(), topic, eventKey);
            return saved;
        } catch (Exception ex) {
            log.error("Failed to append outbox event to OUTBOX table: topic={}, key={}", topic, eventKey, ex);
            throw new RuntimeException("Failed to append transactional outbox event", ex);
        }
    }
}
