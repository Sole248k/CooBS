package com.group2.fse.orchestration_service.saga;

import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.orchestration_service.client.DownstreamException;
import com.group2.fse.orchestration_service.client.InternalApi;
import com.group2.fse.orchestration_service.client.LedgerClient;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartReversalRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Reversal and compensation saga coordinator (O2 / FIN-104).
 * Reverses a posted transfer through L7, or releases an active hold if the transfer was
 * stopped before capture. Emits stage and notification events upon outcome.
 */
@Slf4j
@Service
public class ReversalSaga {

    private final SagaRepository sagas;
    private final LedgerClient ledger;
    private final StageEvents events;
    private final ObjectMapper objectMapper;

    public ReversalSaga(SagaRepository sagas, LedgerClient ledger, StageEvents events,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.sagas = sagas;
        this.ledger = ledger;
        this.events = events;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Executes the reversal saga for request r.
     */
    public void run(StartReversalRequest r, String correlationId) {
        MDC.put(InternalApi.CORRELATION_MDC_KEY, correlationId);
        try {
            SagaInstance reversalSaga = sagas.findOrCreate(r.transactionId());
            if (reversalSaga.getState() != SagaState.STARTED) {
                return;
            }

            if (reversalSaga.getReferenceNo() == null) {
                reversalSaga.setReferenceNo(r.referenceNo());
            }
            if (reversalSaga.getPayload() == null) {
                try {
                    reversalSaga.setPayload(objectMapper.writeValueAsString(r));
                } catch (Exception e) {
                    log.warn("Failed to serialize reversal payload for {}: {}", r.transactionId(), e.getMessage());
                }
            }
            sagas.saveAndFlush(reversalSaga);

            Optional<SagaInstance> originalOpt = sagas.findByTransactionId(r.reversesTransactionId());
            if (originalOpt.isEmpty()) {
                fail(reversalSaga, r, "TRANSACTION_NOT_FOUND", "Original transaction not found: " + r.reversesTransactionId());
                return;
            }

            SagaInstance original = originalOpt.get();

            if (original.getState() == SagaState.POSTED || original.getState() == SagaState.COMPLETED) {
                reversePostedTransfer(reversalSaga, original, r);
            } else if (original.getState() == SagaState.FUNDS_RESERVED) {
                cancelHeldTransfer(reversalSaga, original, r);
            } else if (original.getState() == SagaState.STARTED || original.getState() == SagaState.RISK_APPROVED) {
                cancelUnreservedTransfer(reversalSaga, original, r);
            } else {
                fail(reversalSaga, r, "ALREADY_TERMINAL", "Original transaction is already in terminal state " + original.getState());
            }
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }

    private void reversePostedTransfer(SagaInstance reversalSaga, SagaInstance original, StartReversalRequest r) {
        String origRef = original.getReferenceNo() != null ? original.getReferenceNo() : "UNKNOWN";
        try {
            ledger.reverse(r.transactionId(), new LedgerClient.ReversalRequest(
                    origRef, r.referenceNo(), r.reason(), r.actorId()));

            reversalSaga.setState(SagaState.COMPLETED);
            sagas.saveAndFlush(reversalSaga);

            // Publish reversal stage events
            events.passed(r.transactionId(), r.referenceNo(), Stage.POSTED, "Reversal posted for " + origRef);
            events.passed(r.transactionId(), r.referenceNo(), Stage.LEDGER_UPDATE, null);
            events.passed(r.transactionId(), r.referenceNo(), Stage.NOTIFICATION, null);
            events.passed(r.transactionId(), r.referenceNo(), Stage.RECONCILIATION, null);
        } catch (DownstreamException e) {
            log.error("Reversal of transaction {} failed at ledger: {}", r.transactionId(), e.getMessage());
            fail(reversalSaga, r, e.getErrorCode(), e.getMessage());
        }
    }

    private void cancelHeldTransfer(SagaInstance reversalSaga, SagaInstance original, StartReversalRequest r) {
        try {
            ledger.release(original.getTransactionId(), original.getHoldId(), "REVERSAL_REQUESTED: " + r.reason());

            original.setState(SagaState.COMPENSATED);
            sagas.saveAndFlush(original);

            reversalSaga.setState(SagaState.COMPLETED);
            sagas.saveAndFlush(reversalSaga);

            events.failed(original.getTransactionId(), original.getReferenceNo(), Stage.POSTED, "REVERSED", "Hold released due to reversal");
            events.passed(r.transactionId(), r.referenceNo(), Stage.POSTED, "Hold cancelled and funds released");
            events.passed(r.transactionId(), r.referenceNo(), Stage.NOTIFICATION, null);
        } catch (DownstreamException e) {
            log.error("Failed to release hold {} for reversal {}: {}", original.getHoldId(), r.transactionId(), e.getMessage());
            fail(reversalSaga, r, e.getErrorCode(), e.getMessage());
        }
    }

    private void cancelUnreservedTransfer(SagaInstance reversalSaga, SagaInstance original, StartReversalRequest r) {
        original.setState(SagaState.FAILED);
        original.setFailureCode("CANCELLED_BEFORE_HOLD");
        sagas.saveAndFlush(original);

        reversalSaga.setState(SagaState.COMPLETED);
        sagas.saveAndFlush(reversalSaga);

        events.passed(r.transactionId(), r.referenceNo(), Stage.POSTED, "Pre-hold transaction cancelled");
        events.passed(r.transactionId(), r.referenceNo(), Stage.NOTIFICATION, null);
    }

    private void fail(SagaInstance reversalSaga, StartReversalRequest r, String failureCode, String detail) {
        reversalSaga.setFailureCode(failureCode);
        reversalSaga.setState(SagaState.FAILED);
        sagas.saveAndFlush(reversalSaga);
        events.failed(r.transactionId(), r.referenceNo(), Stage.POSTED, failureCode, detail);
    }
}
