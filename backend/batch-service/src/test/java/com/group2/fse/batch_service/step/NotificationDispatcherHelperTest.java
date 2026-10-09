package com.group2.fse.batch_service.step;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatcherHelper Unit Tests")
class NotificationDispatcherHelperTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private NotificationDispatcherHelper helperWithKafka() {
        NotificationDispatcherHelper helper = new NotificationDispatcherHelper();
        ReflectionTestUtils.setField(helper, "kafkaTemplate", kafkaTemplate);
        return helper;
    }

    @Test
    @DisplayName("Publishes one INTEREST_CREDIT event, keyed by account, in the shape notification-service reads")
    @SuppressWarnings("unchecked")
    void publishesInterestCreditEvent() {
        helperWithKafka().dispatchInterestNotification(1L, 7L, "1000000007", new BigDecimal("12.3456"), "INT-202610-7");

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("ledger.mutation.completed.v1"), eq("7"), sent.capture());

        Map<String, Object> event = (Map<String, Object>) sent.getValue();
        // notification-service skips an event whose eventId it has already stored.
        assertThat((String) event.get("eventId")).startsWith("evt_int_INT-202610-7_");
        assertThat(event).containsKeys("eventType", "timestamp", "version");

        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(payload)
                .containsEntry("transactionType", "INTEREST_CREDIT")
                .containsEntry("customerId", 1L)
                .containsEntry("accountId", 7L)
                .containsEntry("accountNumber", "1000000007")
                .containsEntry("referenceNo", "INT-202610-7")
                // Money is a string with two decimals.
                .containsEntry("amount", "12.35");
    }

    @Test
    @DisplayName("A broker failure does not fail the interest step")
    void brokerFailureIsNotThrown() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any())).thenThrow(new RuntimeException("broker down"));

        assertThatCode(() -> helperWithKafka()
                .dispatchInterestNotification(1L, 7L, "1000000007", new BigDecimal("10.00"), "INT-202610-7"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Without Kafka nothing is sent and nothing is thrown")
    void withoutKafkaNothingIsSent() {
        assertThatCode(() -> new NotificationDispatcherHelper()
                .dispatchInterestNotification(1L, 7L, "1000000007", new BigDecimal("10.00"), "INT-202610-7"))
                .doesNotThrowAnyException();
    }
}
