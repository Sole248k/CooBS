package com.group2.fse.orchestration_service.event;

import java.util.UUID;
import java.util.function.BiConsumer;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.client.InternalApi;
import com.group2.fse.orchestration_service.saga.TransferSaga;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The events that arrive after posting and complete stages 9 to 11. */
@Component
@RequiredArgsConstructor
public class PostingEventListener {

    private final TransferSaga transferSaga;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${app.kafka.topics.ledger-transfer-completed:ledger.transfer.completed.v1}")
    public void onLedgerTransferCompleted(String message) {
        handle(message, "transferReference", transferSaga::onLedgerTransferCompleted);
    }

    @KafkaListener(topics = "${app.kafka.topics.notification-dispatched:notification.dispatched.v1}")
    public void onNotificationDispatched(String message) {
        handle(message, "referenceNo", transferSaga::onNotificationDispatched);
    }

    private void handle(String message, String referenceField, BiConsumer<UUID, String> handler) {
        JsonNode event = jsonMapper.readTree(message);
        JsonNode payload = event.path("payload");
        // Staff-console transfers post straight to the ledger and carry no transactionId.
        String transactionId = payload.path("transactionId").asString(null);
        if (transactionId == null) {
            return;
        }
        MDC.put(InternalApi.CORRELATION_MDC_KEY, event.path("correlationId").asString(null));
        try {
            handler.accept(UUID.fromString(transactionId), payload.path(referenceField).asString(null));
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }
}
