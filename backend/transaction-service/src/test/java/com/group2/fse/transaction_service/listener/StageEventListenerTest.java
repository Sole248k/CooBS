package com.group2.fse.transaction_service.listener;

import com.group2.fse.transaction_service.event.StageChangedEvent;
import com.group2.fse.transaction_service.service.TimelineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StageEventListener Unit Tests (FIN-304)")
class StageEventListenerTest {

    @Mock
    private TimelineService timelineService;

    @InjectMocks
    private StageEventListener stageEventListener;

    @Test
    @DisplayName("onStageChanged forwards stage payload to TimelineService.recordStage")
    void testOnStageChanged() {
        UUID txnId = UUID.randomUUID();
        Instant now = Instant.now();
        StageChangedEvent.Payload payload = new StageChangedEvent.Payload(
                txnId,
                "TX-20261006-A1B2C3D4",
                4,
                "FRAUD_CHECK",
                "PASSED",
                "PROCESSING",
                null,
                "score 0.15 (Low Risk)",
                null,
                now
        );

        StageChangedEvent event = new StageChangedEvent(
                "evt-123",
                StageChangedEvent.EVENT_TYPE,
                now,
                "1.0",
                "corr-456",
                payload
        );

        stageEventListener.onStageChanged(event);

        verify(timelineService).recordStage(
                eq(txnId),
                eq(4),
                eq("FRAUD_CHECK"),
                eq("PASSED"),
                eq("score 0.15 (Low Risk)"),
                eq(now),
                eq("PROCESSING"),
                eq(null)
        );
    }
}
