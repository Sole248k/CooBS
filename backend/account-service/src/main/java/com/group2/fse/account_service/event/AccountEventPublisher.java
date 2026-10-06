package com.group2.fse.account_service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${account.kafka.topics.closure:account.closure.completed.v1}")
    private String closureTopic;

    @Value("${account.kafka.topics.created:account.created.v1}")
    private String createdTopic;

    public void publishAccountClosure(AccountClosureCompletedEvent event) {
        log.info("Publishing AccountClosureCompletedEvent to topic '{}': accountId={}, closureRequestId={}",
                closureTopic, event.getAccountId(), event.getClosureRequestId());
        try {
            kafkaTemplate.send(closureTopic, event.getAccountId().toString(), event);
        } catch (Exception ex) {
            log.error("Failed to publish AccountClosureCompletedEvent to Kafka topic '{}': {}", closureTopic, ex.getMessage());
        }
    }

    public void publishAccountCreated(AccountCreatedEvent event) {
        log.info("Publishing AccountCreatedEvent to topic '{}': accountId={}, accountNumber={}",
                createdTopic, event.getAccountId(), event.getAccountNumber());
        try {
            kafkaTemplate.send(createdTopic, event.getAccountId().toString(), event);
        } catch (Exception ex) {
            log.error("Failed to publish AccountCreatedEvent to Kafka topic '{}': {}", createdTopic, ex.getMessage());
        }
    }
}
