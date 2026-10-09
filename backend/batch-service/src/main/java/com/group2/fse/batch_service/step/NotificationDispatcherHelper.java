package com.group2.fse.batch_service.step;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches customer in-app notifications on month-end interest capitalization
 * via Kafka topic ledger.mutation.completed.v1.
 *
 * notification-service consumes that topic and writes the notification itself, so this
 * on-premises service holds no connection to the cloud notification_db.
 */
@Slf4j
@Component
public class NotificationDispatcherHelper {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void dispatchInterestNotification(Long customerId, Long accountId, String accountNumber, BigDecimal amount, String referenceNo) {
        String eventId = "evt_int_" + referenceNo + "_" + UUID.randomUUID().toString().substring(0, 6);

        if (kafkaTemplate == null) {
            log.warn("Kafka is not configured; no interest notification was sent for account #{}", accountId);
            return;
        }

        // Publish Kafka event to topic ledger.mutation.completed.v1
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", eventId);
            event.put("eventType", "LEDGER_MUTATION_COMPLETED");
            event.put("timestamp", Instant.now().toString());
            event.put("version", "1.0");

            Map<String, Object> payload = new HashMap<>();
            payload.put("transactionId", 0L);
            payload.put("accountId", accountId);
            payload.put("accountNumber", accountNumber);
            payload.put("transactionType", "INTEREST_CREDIT");
            payload.put("amount", amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            payload.put("referenceNo", referenceNo);
            payload.put("customerId", customerId != null ? customerId : 1L);
            event.put("payload", payload);

            kafkaTemplate.send("ledger.mutation.completed.v1", String.valueOf(accountId), event);
            log.info("Published INTEREST_CREDIT event to Kafka topic [ledger.mutation.completed.v1] for account #{}", accountId);
        } catch (Exception ex) {
            log.warn("Could not publish Kafka interest notification: {}", ex.getMessage());
        }
    }
}
