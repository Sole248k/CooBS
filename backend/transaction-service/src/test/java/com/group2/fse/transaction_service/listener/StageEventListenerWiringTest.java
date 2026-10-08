package com.group2.fse.transaction_service.listener;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The listener's own tests call it directly, which passes even when nothing ever delivers a
 * record to it. This checks that the application context really registers it on the topic.
 */
@SpringBootTest
@DisplayName("Stage event listener is registered with Kafka")
class StageEventListenerWiringTest {

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;

    @Test
    @DisplayName("a listener container exists for transaction.stage.changed.v1 in the service's consumer group")
    void stageEventListenerIsRegisteredOnTheStageTopic() {
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();

        assertThat(containers).hasSize(1);
        MessageListenerContainer container = containers.iterator().next();
        assertThat(container.getContainerProperties().getTopics()).containsExactly("transaction.stage.changed.v1");
        assertThat(container.getGroupId()).isEqualTo("transaction-service-group");
    }

    @Test
    @DisplayName("records reach the listener as strings: orchestration-service sends no type headers")
    void consumerDeserializesValuesAsStrings() {
        assertThat(kafkaListenerContainerFactory.getConsumerFactory().getConfigurationProperties()
                .get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
    }
}
