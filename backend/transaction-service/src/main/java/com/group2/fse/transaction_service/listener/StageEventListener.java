package com.group2.fse.transaction_service.listener;

import com.group2.fse.transaction_service.event.StageChangedEvent;
import com.group2.fse.transaction_service.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes transaction.stage.changed.v1 events and updates stage history
 * and transaction lifecycle status in transaction_db.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageEventListener {

    private final TimelineService timelineService;

    @KafkaListener(
            topics = "${app.kafka.topics.stage-changed:transaction.stage.changed.v1}",
            groupId = "${spring.kafka.consumer.group-id:transaction-service-group}"
    )
    public void onStageChanged(StageChangedEvent event) {
        if (event == null || event.payload() == null) {
            log.warn("Discarding null stage changed event or payload");
            return;
        }

        StageChangedEvent.Payload p = event.payload();
        log.info("Processing stage changed event for txn={}: stageNo={}, stageName={}, outcome={}, status={}",
                p.transactionId(), p.stageNo(), p.stageName(), p.outcome(), p.transactionStatus());

        timelineService.recordStage(
                p.transactionId(),
                p.stageNo(),
                p.stageName(),
                p.outcome(),
                p.detail(),
                p.occurredAt(),
                p.transactionStatus(),
                p.failureCode()
        );
    }
}
