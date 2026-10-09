package com.group2.fse.notification_service.listener;

import com.group2.fse.notification_service.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"ledger.mutation.completed.v1", "notification.dispatched.v1", "notification.events.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "notification.kafka.auto-startup=true"
})
@DisplayName("A record sent as plain JSON text, the way the dead-letter replay sends it, is delivered")
class KafkaReplayedEventDeliveryIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("Plain JSON text with no key and no type headers becomes a notification")
    void plainJsonTextIsDelivered() throws Exception {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        String eventId = "evt_replay_test_" + System.currentTimeMillis();
        // NotificationDlqService.replayMessage sends the parked value back exactly like this:
        // a String, with the original key (which may be null) and no type headers.
        String json = "{\"eventId\":\"" + eventId + "\",\"timestamp\":\"2026-10-09T02:13:08Z\",\"version\":\"1.0\","
                + "\"eventType\":\"LEDGER_MUTATION_COMPLETED\",\"payload\":{\"newBalance\":\"1.00\","
                + "\"referenceNo\":\"DLQ-TEST-0081bad2\",\"transactionType\":\"CREDIT\",\"customerId\":1,"
                + "\"transactionId\":0,\"accountNumber\":\"DLQ-TEST-0081bad2\",\"previousBalance\":\"0.00\","
                + "\"accountId\":1,\"amount\":\"1.00\"}}";

        template.send("ledger.mutation.completed.v1", null, json).get(5, TimeUnit.SECONDS);

        long startTime = System.currentTimeMillis();
        boolean found = false;
        while (System.currentTimeMillis() - startTime < 15000) {
            if (notificationRepository.existsByEventId(eventId)) {
                found = true;
                break;
            }
            Thread.sleep(200);
        }
        assertThat(found).isTrue();
    }
}
