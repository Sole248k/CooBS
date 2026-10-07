package com.group2.fse.notification_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Task: FSE-601
 * Apache Kafka consumer infrastructure configuration for In-App Notifications.
 * Configures ConsumerFactory with StringJsonMessageConverter to seamlessly
 * convert JSON payloads into domain events across microservices.
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notification-service-group}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${notification.kafka.auto-startup:true}")
    private boolean autoStartup;

    @Value("${notification.kafka.topics.dlt:notification.events.DLT}")
    private String dltTopic;

    @Value("${notification.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${notification.kafka.retry.initial-interval-ms:1000}")
    private long initialIntervalMs;

    @Value("${notification.kafka.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public RecordMessageConverter recordMessageConverter(ObjectMapper objectMapper) {
        return new StringJsonMessageConverter(objectMapper);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            org.springframework.kafka.core.KafkaTemplate<String, String> stringKafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                stringKafkaTemplate,
                (record, ex) -> {
                    log.warn("Routing poison record from topic [{}] to DLT [{}] after retry exhaustion. Cause: {}",
                            record.topic(), dltTopic, ex != null ? ex.getMessage() : "unknown");
                    return new org.apache.kafka.common.TopicPartition(dltTopic, -1);
                }
        );

        recoverer.setHeadersFunction((record, ex) -> {
            org.apache.kafka.common.header.internals.RecordHeaders headers = new org.apache.kafka.common.header.internals.RecordHeaders();
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader("x-original-topic", record.topic().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            String errorMsg = ex != null && ex.getMessage() != null ? ex.getMessage() : (ex != null ? ex.getClass().getName() : "Unknown failure");
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader("x-exception-message", errorMsg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader("x-attempts", String.valueOf(maxAttempts).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            headers.add(new org.apache.kafka.common.header.internals.RecordHeader("x-failed-at", java.time.Instant.now().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return headers;
        });

        return recoverer;
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        org.springframework.kafka.support.ExponentialBackOffWithMaxRetries backOff =
                new org.springframework.kafka.support.ExponentialBackOffWithMaxRetries(maxAttempts);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(retryMultiplier);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka consumer delivery attempt [{}/{}] failed on topic [{}], partition [{}], offset [{}]: {}",
                        deliveryAttempt, maxAttempts,
                        record != null ? record.topic() : "unknown",
                        record != null ? record.partition() : -1,
                        record != null ? record.offset() : -1,
                        ex != null ? ex.getMessage() : "unknown"));
        return errorHandler;
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic notificationEventsDltTopic() {
        return org.springframework.kafka.config.TopicBuilder.name(dltTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler defaultErrorHandler,
            RecordMessageConverter recordMessageConverter) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(defaultErrorHandler);
        factory.setRecordMessageConverter(recordMessageConverter);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
