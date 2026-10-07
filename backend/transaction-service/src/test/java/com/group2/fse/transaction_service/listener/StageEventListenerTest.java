package com.group2.fse.transaction_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.transaction_service.service.TimelineService;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("StageEventListener Unit Tests (FIN-304)")
class StageEventListenerTest {

    /** What orchestration-service puts on transaction.stage.changed.v1: a JSON string, no type headers. */
    private static final String FRAUD_CHECK_PASSED = """
            {
              "eventId": "evt_3b1c",
              "eventType": "TRANSACTION_STAGE_CHANGED",
              "timestamp": "2026-10-03T11:40:00.060Z",
              "version": "1.0",
              "correlationId": "e7b8c1",
              "payload": {
                "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
                "referenceNo": "TX-20261003-8F3A21C0",
                "stageNo": 4,
                "stageName": "FRAUD_CHECK",
                "outcome": "PASSED",
                "transactionStatus": "PROCESSING",
                "failureCode": null,
                "detail": "score 0.15",
                "customerMessage": null,
                "occurredAt": "2026-10-03T11:40:00.060Z"
              }
            }
            """;

    @Mock
    private TimelineService timelineService;

    private StageEventListener stageEventListener;

    @BeforeEach
    void setUp() {
        stageEventListener = new StageEventListener(timelineService,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    @DisplayName("onStageChanged maps the JSON string and forwards the stage to TimelineService.recordStage")
    void testOnStageChanged() {
        stageEventListener.onStageChanged(FRAUD_CHECK_PASSED);

        verify(timelineService).recordStage(
                eq(UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")),
                eq(4),
                eq("FRAUD_CHECK"),
                eq("PASSED"),
                eq("score 0.15"),
                eq(Instant.parse("2026-10-03T11:40:00.060Z")),
                eq("PROCESSING"),
                eq(null)
        );
    }

    @Test
    @DisplayName("onStageChanged accepts a failed stage and a field this service does not know")
    void testOnStageChangedFailedStageWithUnknownField() {
        String failed = FRAUD_CHECK_PASSED
                .replace("\"outcome\": \"PASSED\"", "\"outcome\": \"FAILED\"")
                .replace("\"transactionStatus\": \"PROCESSING\"", "\"transactionStatus\": \"FAILED\"")
                .replace("\"failureCode\": null", "\"failureCode\": \"HIGH_RISK\", \"addedLater\": true");

        stageEventListener.onStageChanged(failed);

        verify(timelineService).recordStage(
                eq(UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")),
                eq(4),
                eq("FRAUD_CHECK"),
                eq("FAILED"),
                eq("score 0.15"),
                eq(Instant.parse("2026-10-03T11:40:00.060Z")),
                eq("FAILED"),
                eq("HIGH_RISK")
        );
    }

    @Test
    @DisplayName("onStageChanged skips a record that is not a stage event instead of failing the consumer")
    void testOnStageChangedSkipsUnreadableRecords() {
        stageEventListener.onStageChanged("not json");
        stageEventListener.onStageChanged("");
        stageEventListener.onStageChanged("{\"eventId\": \"evt_1\"}");

        verifyNoInteractions(timelineService);
    }

    @Test
    @DisplayName("the configured consumer deserializer hands the listener the record as a string")
    void testConfiguredDeserializerYieldsTheStringTheListenerReads() throws Exception {
        Properties properties = new Properties();
        // The main file, not the test one that shadows it on the classpath.
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        String deserializerClass = properties.getProperty("spring.kafka.consumer.value-deserializer");

        Object value;
        try (Deserializer<?> deserializer = (Deserializer<?>) Class.forName(deserializerClass)
                .getDeclaredConstructor().newInstance()) {
            // No headers: orchestration-service sends none, which a typed JSON deserializer cannot handle.
            value = deserializer.deserialize("transaction.stage.changed.v1",
                    FRAUD_CHECK_PASSED.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(value).isInstanceOf(String.class);
        stageEventListener.onStageChanged((String) value);
        verify(timelineService).recordStage(
                eq(UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")),
                eq(4), eq("FRAUD_CHECK"), eq("PASSED"), eq("score 0.15"),
                eq(Instant.parse("2026-10-03T11:40:00.060Z")), eq("PROCESSING"), eq(null));
    }
}
