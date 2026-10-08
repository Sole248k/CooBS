package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches customer in-app notifications on month-end interest capitalization
 * both via direct DB persistence into notification_db and Kafka topic ledger.mutation.completed.v1.
 */
@Slf4j
@Component
public class NotificationDispatcherHelper {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${POSTGRES_CLOUD_HOST:postgres-cloud-db}")
    private String cloudHost;

    @Value("${POSTGRES_CLOUD_PORT:5432}")
    private String cloudPort;

    @Value("${POSTGRES_CLOUD_DB:notification_db}")
    private String cloudDb;

    @Value("${POSTGRES_CLOUD_USER:postgres}")
    private String cloudUser;

    @Value("${POSTGRES_CLOUD_PASSWORD:postgres}")
    private String cloudPassword;

    public void dispatchInterestNotification(Long customerId, Long accountId, String accountNumber, BigDecimal amount, String referenceNo) {
        String title = "Monthly Interest Credited";
        String message = String.format("Monthly Interest Credited: ₱%s has been added to your savings account %s.",
                amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), accountNumber);
        String eventId = "evt_int_" + referenceNo + "_" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Direct write to postgres-cloud-db / notification_db (guaranteed instant delivery)
        String[] hosts = new String[]{cloudHost, "postgres-cloud-db", "localhost", "127.0.0.1"};
        int[] ports = new int[]{5432, 5435};

        boolean directDbSuccess = false;
        for (String h : hosts) {
            if (directDbSuccess) break;
            for (int p : ports) {
                String jdbcUrl = "jdbc:postgresql://" + h + ":" + p + "/" + cloudDb;
                try (Connection conn = DriverManager.getConnection(jdbcUrl, cloudUser, cloudPassword);
                     PreparedStatement stmt = conn.prepareStatement(
                             "INSERT INTO public.notifications (customer_id, title, message, channel, is_read, event_id, created_at) " +
                             "VALUES (?, ?, ?, 'IN_APP', false, ?, ?)")) {
                    stmt.setLong(1, customerId != null ? customerId : 1L);
                    stmt.setString(2, title);
                    stmt.setString(3, message);
                    stmt.setString(4, eventId);
                    stmt.setTimestamp(5, Timestamp.from(Instant.now()));
                    stmt.executeUpdate();
                    log.info("Directly wrote in-app notification to notification_db for customer #{}: {}", customerId, message);
                    directDbSuccess = true;
                    break;
                } catch (Exception ex) {
                    // Try next host/port combo
                }
            }
        }

        // 2. Publish Kafka event to topic ledger.mutation.completed.v1
        if (kafkaTemplate != null) {
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
}
