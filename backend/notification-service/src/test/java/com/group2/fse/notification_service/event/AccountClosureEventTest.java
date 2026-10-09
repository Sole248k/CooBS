package com.group2.fse.notification_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountClosureEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should successfully deserialize flat account-service closure event")
    void shouldDeserializeFlatAccountServiceEvent() throws Exception {
        String flatJson = """
                {
                  "closureRequestId": 88,
                  "accountId": 4,
                  "accountNumber": "ACC_10000004",
                  "customerId": 101,
                  "approvedBy": 1,
                  "timestamp": "2026-10-09T15:47:33"
                }
                """;

        AccountClosureEvent event = objectMapper.readValue(flatJson, AccountClosureEvent.class);

        assertThat(event).isNotNull();
        assertThat(event.getEventId()).isEqualTo("EVT-CLOSURE-88");
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getPayload()).isNotNull();
        assertThat(event.getPayload().getClosureRequestId()).isEqualTo(88L);
        assertThat(event.getPayload().getAccountId()).isEqualTo(4L);
        assertThat(event.getPayload().getAccountNumber()).isEqualTo("ACC_10000004");
        assertThat(event.getPayload().getCustomerId()).isEqualTo(101L);
        assertThat(event.getPayload().getApprovedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should successfully deserialize envelope closure event with ISO Instant")
    void shouldDeserializeEnvelopeEvent() throws Exception {
        String envelopeJson = """
                {
                  "eventId": "evt_closure_001",
                  "eventType": "ACCOUNT_CLOSURE_COMPLETED",
                  "timestamp": "2026-10-09T15:47:33Z",
                  "version": "1.0",
                  "payload": {
                    "closureRequestId": 88,
                    "accountId": 4,
                    "accountNumber": "ACC_10000004",
                    "customerId": 101,
                    "approvedBy": 1,
                    "finalBalance": "0.00"
                  }
                }
                """;

        AccountClosureEvent event = objectMapper.readValue(envelopeJson, AccountClosureEvent.class);

        assertThat(event).isNotNull();
        assertThat(event.getEventId()).isEqualTo("evt_closure_001");
        assertThat(event.getPayload()).isNotNull();
        assertThat(event.getPayload().getAccountNumber()).isEqualTo("ACC_10000004");
        assertThat(event.getPayload().getCustomerId()).isEqualTo(101L);
    }
}
