package com.group2.fse.notification_service.service;

import com.group2.fse.notification_service.dto.DlqMessageDto;
import com.group2.fse.notification_service.dto.DlqReplayResponseDto;
import com.group2.fse.notification_service.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Task: Dev 3 Kafka DLQ & Admin Replay
 * Service providing inspection and replay capabilities for poison pill events routed to Kafka DLT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDlqService {

    private final KafkaTemplate<String, String> stringKafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${notification.kafka.topics.dlt:notification.events.DLT}")
    private String dltTopic;

    @Value("${notification.kafka.topics.mutation:ledger.mutation.completed.v1}")
    private String fallbackOriginalTopic;

    /**
     * Inspects and retrieves all currently available messages in the DLT topic.
     *
     * @return list of DlqMessageDto conforming with contract T6.
     */
    public List<DlqMessageDto> getDlqMessages() {
        log.info("Reading DLQ messages from topic [{}]", dltTopic);
        try (Consumer<String, String> consumer = createTransientConsumer()) {
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(dltTopic);
            if (partitionInfos == null || partitionInfos.isEmpty()) {
                log.info("No partition metadata found for DLQ topic [{}]", dltTopic);
                return Collections.emptyList();
            }

            List<TopicPartition> partitions = partitionInfos.stream()
                    .map(p -> new TopicPartition(p.topic(), p.partition()))
                    .toList();

            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1500));
            List<DlqMessageDto> dlqList = new ArrayList<>();

            for (ConsumerRecord<String, String> record : records) {
                dlqList.add(mapRecordToDto(record));
            }

            log.info("Retrieved [{}] messages from DLQ topic [{}]", dlqList.size(), dltTopic);
            return dlqList;
        } catch (Exception ex) {
            log.error("Failed to read messages from DLQ topic [{}]: {}", dltTopic, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Replays a specific poison pill message by recordId back to its original target Kafka topic.
     *
     * @param recordId identifier in format <topic>-<partition>-<offset> (e.g. notification.events.DLT-0-42)
     * @return DlqReplayResponseDto confirming replay status conforming with contract T7.
     */
    public DlqReplayResponseDto replayMessage(String recordId) {
        log.info("Request to replay DLQ record: {}", recordId);
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("Record ID cannot be empty");
        }

        ParsedRecordId parsed = parseRecordId(recordId);

        TopicPartition topicPartition = new TopicPartition(parsed.topic, parsed.partition);
        try (Consumer<String, String> consumer = createTransientConsumer()) {
            consumer.assign(Collections.singletonList(topicPartition));
            consumer.seek(topicPartition, parsed.offset);

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2500));
            ConsumerRecord<String, String> targetRecord = null;

            for (ConsumerRecord<String, String> record : records) {
                if (record.offset() == parsed.offset) {
                    targetRecord = record;
                    break;
                }
            }

            if (targetRecord == null) {
                throw new ResourceNotFoundException("DLQ record [" + recordId + "] not found on partition " + parsed.partition);
            }

            String originalTopic = resolveOriginalTopic(targetRecord);
            log.info("Replaying record [{}] with key [{}] back to original topic [{}]",
                    recordId, targetRecord.key(), originalTopic);

            stringKafkaTemplate.send(originalTopic, targetRecord.key(), targetRecord.value())
                    .get(5, TimeUnit.SECONDS);

            return DlqReplayResponseDto.builder()
                    .recordId(recordId)
                    .replayed(true)
                    .replayedAt(Instant.now().toString())
                    .build();
        } catch (ResourceNotFoundException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error during DLQ replay for record [{}]: {}", recordId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to replay DLQ record [" + recordId + "]: " + ex.getMessage(), ex);
        }
    }

    /**
     * Replays all available dead-letter messages back to their respective original topics.
     *
     * @return list of replay confirmations.
     */
    public List<DlqReplayResponseDto> replayAll() {
        List<DlqMessageDto> messages = getDlqMessages();
        List<DlqReplayResponseDto> responses = new ArrayList<>();

        for (DlqMessageDto message : messages) {
            try {
                responses.add(replayMessage(message.getRecordId()));
            } catch (Exception ex) {
                log.error("Failed replaying item [{}]: {}", message.getRecordId(), ex.getMessage());
            }
        }

        return responses;
    }

    private DlqMessageDto mapRecordToDto(ConsumerRecord<String, String> record) {
        String recordId = record.topic() + "-" + record.partition() + "-" + record.offset();
        String originalTopic = resolveOriginalTopic(record);
        String errorMessage = getHeaderValue(record.headers(), "x-exception-message", "kafka_dlt-exception-message");
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = "Unknown processing exception";
        }

        Integer attempts = 3;
        String attemptsStr = getHeaderValue(record.headers(), "x-attempts");
        if (attemptsStr != null) {
            try {
                attempts = Integer.parseInt(attemptsStr);
            } catch (NumberFormatException ignored) {}
        }

        String failedAt = getHeaderValue(record.headers(), "x-failed-at");
        if (failedAt == null || failedAt.isBlank()) {
            failedAt = Instant.ofEpochMilli(record.timestamp()).toString();
        }

        return DlqMessageDto.builder()
                .recordId(recordId)
                .topic(record.topic())
                .originalTopic(originalTopic)
                .key(record.key())
                .errorMessage(errorMessage)
                .attempts(attempts)
                .failedAt(failedAt)
                .payload(record.value())
                .build();
    }

    private String resolveOriginalTopic(ConsumerRecord<String, String> record) {
        String originalTopic = getHeaderValue(record.headers(), "x-original-topic", "kafka_dlt-original-topic");
        if (originalTopic != null && !originalTopic.isBlank()) {
            return originalTopic;
        }

        if (record.topic().endsWith(".DLT")) {
            return record.topic().substring(0, record.topic().length() - 4);
        }

        return fallbackOriginalTopic;
    }

    private String getHeaderValue(Headers headers, String... headerNames) {
        if (headers == null) {
            return null;
        }
        for (String name : headerNames) {
            Header header = headers.lastHeader(name);
            if (header != null && header.value() != null) {
                return new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private ParsedRecordId parseRecordId(String recordId) {
        int lastHyphen = recordId.lastIndexOf('-');
        if (lastHyphen <= 0) {
            throw new IllegalArgumentException("Invalid recordId format. Expected <topic>-<partition>-<offset>");
        }
        long offset;
        try {
            offset = Long.parseLong(recordId.substring(lastHyphen + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid offset in recordId: " + recordId);
        }

        int secondLastHyphen = recordId.lastIndexOf('-', lastHyphen - 1);
        if (secondLastHyphen <= 0) {
            throw new IllegalArgumentException("Invalid recordId format. Expected <topic>-<partition>-<offset>");
        }
        int partition;
        try {
            partition = Integer.parseInt(recordId.substring(secondLastHyphen + 1, lastHyphen));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid partition in recordId: " + recordId);
        }

        String topic = recordId.substring(0, secondLastHyphen);
        return new ParsedRecordId(topic, partition, offset);
    }

    private Consumer<String, String> createTransientConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-admin-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new KafkaConsumer<>(props);
    }

    private record ParsedRecordId(String topic, int partition, long offset) {}
}
