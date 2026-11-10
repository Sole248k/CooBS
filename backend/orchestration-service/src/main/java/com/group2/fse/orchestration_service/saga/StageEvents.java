package com.group2.fse.orchestration_service.saga;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.event.StageEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage events as the sagas publish them. The saga row is already saved when a stage is
 * published, so an event that cannot be sent (broker down, send rejected) is logged and
 * dropped here: it must never stop the saga before its next ledger call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StageEvents {

    private final StageEventPublisher publisher;

    void passed(UUID transactionId, String referenceNo, Stage stage, String detail) {
        try {
            publisher.passed(transactionId, referenceNo, stage, detail);
        } catch (RuntimeException e) {
            notPublished(transactionId, stage, e);
        }
    }

    void failed(UUID transactionId, String referenceNo, Stage stage, String failureCode, String detail) {
        try {
            publisher.failed(transactionId, referenceNo, stage, failureCode, detail);
        } catch (RuntimeException e) {
            notPublished(transactionId, stage, e);
        }
    }

    private static void notPublished(UUID transactionId, Stage stage, RuntimeException e) {
        log.error("Stage event {} for transaction {} was not published; the saga continues: {}",
                stage, transactionId, e.getMessage(), e);
    }
}
