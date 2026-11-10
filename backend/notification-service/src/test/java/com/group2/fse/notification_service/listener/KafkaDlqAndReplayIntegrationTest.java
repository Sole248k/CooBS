package com.group2.fse.notification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.notification_service.dto.DlqMessageDto;
import com.group2.fse.notification_service.dto.DlqReplayResponseDto;
import com.group2.fse.notification_service.security.JwtTokenProvider;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "ledger.mutation.completed.v1",
                "notification.dispatched.v1",
                "notification.events.DLT"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "notification.kafka.auto-startup=true",
        "notification.kafka.topics.dlt=notification.events.DLT",
        "notification.kafka.retry.max-attempts=3",
        "notification.kafka.retry.initial-interval-ms=100",
        "notification.kafka.retry.multiplier=1.5"
})
@DisplayName("Dev 3: Kafka 3-Attempt Exponential Backoff, DLQ Routing & Admin Replay Test")
class KafkaDlqAndReplayIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private KafkaTemplate<String, String> rawKafkaTemplate;
    private String adminJwtToken;
    private String customerJwtToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        rawKafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        adminJwtToken = jwtTokenProvider.generateToken("admin_user", 999L, List.of("ADMIN"));
        customerJwtToken = jwtTokenProvider.generateToken("customer_user", 101L, List.of("CUSTOMER"));
    }

    @Test
    @DisplayName("Should route poison pill to notification.events.DLT after retry exhaustion and replay successfully via Admin API")
    void shouldRoutePoisonPillToDltAndReplay() throws Exception {
        String poisonKey = "poison-test-" + System.currentTimeMillis();
        // A genuine unparseable poison pill string that cannot be parsed as JSON
        String poisonPayload = "NOT_A_VALID_JSON_POISON_PILL:{" + poisonKey + "}";

        // 1. Send poison pill to ledger.mutation.completed.v1
        rawKafkaTemplate.send("ledger.mutation.completed.v1", poisonKey, poisonPayload).get(5, TimeUnit.SECONDS);

        // 2. Poll notification.events.DLT to verify DeadLetterPublishingRecoverer delivered the message
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verifier-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerRecord<String, String> dltRecord = null;
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
            TopicPartition dltPartition = new TopicPartition("notification.events.DLT", 0);
            consumer.assign(Collections.singletonList(dltPartition));
            consumer.seekToBeginning(Collections.singletonList(dltPartition));

            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 10000) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    if (poisonKey.equals(record.key())) {
                        dltRecord = record;
                        break;
                    }
                }
                if (dltRecord != null) {
                    break;
                }
            }
        }

        assertThat(dltRecord).as("Poison record should arrive in notification.events.DLT").isNotNull();
        assertThat(dltRecord.value()).isEqualTo(poisonPayload);

        // 3. Verify Admin DLQ Query Endpoint: GET /api/v1/notifications/admin/dlq
        String dltResponseJson = mockMvc.perform(get("/api/v1/notifications/admin/dlq")
                        .header("Authorization", "Bearer " + adminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DlqMessageDto[] dlqMessages = objectMapper.readValue(dltResponseJson, DlqMessageDto[].class);
        Optional<DlqMessageDto> matchedDto = Arrays.stream(dlqMessages)
                .filter(m -> poisonKey.equals(m.getKey()))
                .findFirst();

        assertThat(matchedDto).isPresent();
        DlqMessageDto targetMessage = matchedDto.get();
        assertThat(targetMessage.getTopic()).isEqualTo("notification.events.DLT");
        assertThat(targetMessage.getOriginalTopic()).isEqualTo("ledger.mutation.completed.v1");
        assertThat(targetMessage.getAttempts()).isGreaterThanOrEqualTo(3);

        // 4. Verify Admin Replay Endpoint: POST /api/v1/notifications/admin/dlq/{recordId}/replay
        mockMvc.perform(post("/api/v1/notifications/admin/dlq/" + targetMessage.getRecordId() + "/replay")
                        .header("Authorization", "Bearer " + adminJwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value(targetMessage.getRecordId()))
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.replayedAt", notNullValue()));

        // 5. Verify security authorization rules
        // Customer role forbidden
        mockMvc.perform(get("/api/v1/notifications/admin/dlq")
                        .header("Authorization", "Bearer " + customerJwtToken))
                .andExpect(status().isForbidden());

        // Anonymous forbidden/unauthorized
        mockMvc.perform(get("/api/v1/notifications/admin/dlq"))
                .andExpect(status().isForbidden());
    }
}
