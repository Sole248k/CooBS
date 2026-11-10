package com.group2.fse.notification_service.listener;

import com.group2.fse.notification_service.domain.Notification;
import com.group2.fse.notification_service.event.LedgerMutationEvent;
import com.group2.fse.notification_service.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"ledger.mutation.completed.v1", "notification.dispatched.v1"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "notification.kafka.auto-startup=true"
})
@DisplayName("FSE-601: Kafka Notification Listener Embedded Integration Test")
class KafkaNotificationListenerIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("Should consume LedgerMutationEvent from embedded Kafka and persist notification")
    void shouldConsumeLedgerMutationEvent() throws Exception {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        KafkaTemplate<String, Object> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        String uniqueEventId = "evt_kafka_test_" + System.currentTimeMillis();
        LedgerMutationEvent event = LedgerMutationEvent.builder()
                .eventId(uniqueEventId)
                .eventType("LEDGER_MUTATION_COMPLETED")
                .timestamp(Instant.now())
                .payload(LedgerMutationEvent.MutationPayload.builder()
                        .transactionId(999L)
                        .accountId(50L)
                        .accountNumber("ACC_50000001")
                        .transactionType("CREDIT")
                        .amount(new BigDecimal("1500.0000"))
                        .previousBalance(new BigDecimal("10000.0000"))
                        .newBalance(new BigDecimal("11500.0000"))
                        .referenceNo("CRD-20260927-001")
                        .customerId(202L)
                        .build())
                .build();

        template.send("ledger.mutation.completed.v1", "50", event).get(5, TimeUnit.SECONDS);

        // Wait up to 10 seconds for Kafka consumer to process and save
        long startTime = System.currentTimeMillis();
        boolean found = false;
        while (System.currentTimeMillis() - startTime < 10000) {
            if (notificationRepository.existsByEventId(uniqueEventId)) {
                found = true;
                break;
            }
            Thread.sleep(200);
        }

        assertThat(found).isTrue();
    }
}
