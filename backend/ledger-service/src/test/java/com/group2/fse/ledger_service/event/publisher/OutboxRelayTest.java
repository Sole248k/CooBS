package com.group2.fse.ledger_service.event.publisher;

import com.group2.fse.ledger_service.entity.OutboxEvent;
import com.group2.fse.ledger_service.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay Unit Tests")
class OutboxRelayTest {

    private static final String TOPIC = "ledger.transfer.completed.v1";
    private static final String PAYLOAD =
            "{\"eventType\":\"LEDGER_TRANSFER_COMPLETED\",\"payload\":{\"transferReference\":\"TX-20261003-8F3A21C0\"}}";

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> outboxKafkaTemplate;

    @Mock
    private SendResult<String, String> sendResult;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventRepository, outboxKafkaTemplate, 1000);
    }

    private OutboxEvent pending(long id, String key) {
        return OutboxEvent.builder()
                .outboxId(id)
                .topic(TOPIC)
                .eventKey(key)
                .payload(PAYLOAD)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("sends the stored JSON text unchanged and stamps the row once the broker has acknowledged")
    void testPublishesStoredJsonAndStampsAfterAck() {
        OutboxEvent event = pending(1000L, "1");
        when(outboxEventRepository.findUnpublishedEvents(any(Pageable.class))).thenReturn(List.of(event));
        when(outboxKafkaTemplate.send(TOPIC, "1", PAYLOAD)).thenReturn(CompletableFuture.completedFuture(sendResult));

        outboxRelay.publishPendingOutboxEvents();

        verify(outboxKafkaTemplate).send(TOPIC, "1", PAYLOAD);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("leaves the row pending when the send fails, and does not send the events behind it")
    void testFailedSendLeavesRowPendingAndStopsTheBatch() {
        OutboxEvent first = pending(1000L, "1");
        OutboxEvent second = pending(1001L, "1");
        when(outboxEventRepository.findUnpublishedEvents(any(Pageable.class))).thenReturn(List.of(first, second));
        when(outboxKafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        outboxRelay.publishPendingOutboxEvents();

        verify(outboxKafkaTemplate).send(TOPIC, "1", PAYLOAD);
        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("leaves the row pending when the broker does not acknowledge in time")
    void testUnacknowledgedSendLeavesRowPending() {
        OutboxEvent event = pending(1000L, "1");
        when(outboxEventRepository.findUnpublishedEvents(any(Pageable.class))).thenReturn(List.of(event));
        when(outboxKafkaTemplate.send(TOPIC, "1", PAYLOAD)).thenReturn(new CompletableFuture<>());
        outboxRelay = new OutboxRelay(outboxEventRepository, outboxKafkaTemplate, 50);

        outboxRelay.publishPendingOutboxEvents();

        assertThat(event.getPublishedAt()).isNull();
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
