package com.group2.fse.orchestration_service.saga;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.orchestration_service.client.CustomerClient;
import com.group2.fse.orchestration_service.client.DownstreamException;
import com.group2.fse.orchestration_service.client.InternalApi;
import com.group2.fse.orchestration_service.client.LedgerClient;
import com.group2.fse.orchestration_service.client.RiskClient;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartTransferRequest;
import com.group2.fse.orchestration_service.event.StageEventPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * The transfer saga (O1). Each step reads the saved state, makes one outbound call, saves
 * the new state and only then publishes the stage event. Nothing is kept in memory between
 * steps, so {@link #run} on a saga that stopped anywhere continues from its saved state;
 * the ledger's idempotency keys make the repeated call safe.
 *
 * <pre>
 * STARTED --risk approves--> RISK_APPROVED --hold placed--> FUNDS_RESERVED --captured--> POSTED --events--> COMPLETED
 *    |                            |                              |
 *    +--declined/unavailable      +--hold refused/unreachable    +--capture refused--> COMPENSATING --released--> COMPENSATED
 *    v                            v
 *  FAILED                       FAILED
 * </pre>
 */
@Slf4j
@Service
public class TransferSaga {

    private final SagaRepository sagas;
    private final CustomerClient customers;
    private final RiskClient risk;
    private final LedgerClient ledger;
    private final StageEventPublisher events;
    private final int captureAttempts;
    private final Duration captureBackoff;
    private final ObjectMapper objectMapper;

    public TransferSaga(SagaRepository sagas, CustomerClient customers, RiskClient risk, LedgerClient ledger,
            StageEventPublisher events,
            @Value("${app.saga.capture-attempts}") int captureAttempts,
            @Value("${app.saga.capture-backoff}") Duration captureBackoff,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.sagas = sagas;
        this.customers = customers;
        this.risk = risk;
        this.ledger = ledger;
        this.events = events;
        this.captureAttempts = captureAttempts;
        this.captureBackoff = captureBackoff;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Runs the saga for this transfer from whatever state its row is in.
     */
    public void run(StartTransferRequest transfer, String correlationId) {
        MDC.put(InternalApi.CORRELATION_MDC_KEY, correlationId);
        try {
            SagaInstance saga = sagas.findOrCreate(transfer.transactionId());
            boolean dirty = false;
            if (saga.getReferenceNo() == null && transfer.referenceNo() != null) {
                saga.setReferenceNo(transfer.referenceNo());
                dirty = true;
            }
            if (saga.getPayload() == null) {
                try {
                    saga.setPayload(objectMapper.writeValueAsString(transfer));
                    dirty = true;
                } catch (Exception e) {
                    log.warn("Failed to serialize transfer payload for {}: {}", transfer.transactionId(), e.getMessage());
                }
            }
            if (dirty) {
                sagas.saveAndFlush(saga);
            }
            if (saga.getState() == SagaState.STARTED) {
                checkRisk(saga, transfer);
            }
            if (saga.getState() == SagaState.RISK_APPROVED) {
                reserveFunds(saga, transfer);
            }
            if (saga.getState() == SagaState.FUNDS_RESERVED) {
                capture(saga, transfer);
            }
            if (saga.getState() == SagaState.COMPENSATING) {
                releaseHold(saga, transfer);
            }
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }

    /** Stages 4 and 5. Fails closed: anything other than a clear APPROVE ends the saga before any hold. */
    private void checkRisk(SagaInstance saga, StartTransferRequest t) {
        RiskClient.Decision decision;
        try {
            CustomerClient.RiskProfile profile = customers.riskProfile(t.customerId());
            // isNewBeneficiary is not part of O1, so it is sent as false until the contract carries it.
            decision = risk.evaluate(new RiskClient.Request(t.transactionId(), t.customerId(), t.amount(),
                    t.currency(), t.channel(), profile.riskScore(), profile.kycTier(), profile.dailyLimit(), false));
        } catch (RuntimeException e) {
            log.warn("Risk check unavailable for transaction {}: {}", t.transactionId(), e.getMessage());
            fail(saga, t, Stage.FRAUD_CHECK, "RISK_UNAVAILABLE", null);
            return;
        }

        saga.setRiskDecision(decision.decision());
        String score = decision.score() == null ? null : "score " + decision.score().toPlainString();
        boolean fraudFailed = decision.fraudCheck() != null && decision.fraudCheck().failed();
        boolean limitFailed = decision.limitCheck() != null && decision.limitCheck().failed();

        if (RiskClient.APPROVE.equals(decision.decision())) {
            save(saga, SagaState.RISK_APPROVED);
            events.passed(t.transactionId(), t.referenceNo(), Stage.FRAUD_CHECK, score);
            events.passed(t.transactionId(), t.referenceNo(), Stage.LIMIT_CHECK, null);
        } else if (limitFailed && !fraudFailed) {
            saga.setFailureCode("LIMIT_EXCEEDED");
            save(saga, SagaState.FAILED);
            events.passed(t.transactionId(), t.referenceNo(), Stage.FRAUD_CHECK, score);
            events.failed(t.transactionId(), t.referenceNo(), Stage.LIMIT_CHECK, "LIMIT_EXCEEDED", null);
        } else {
            String reasons = decision.fraudCheck() == null || decision.fraudCheck().reasons() == null
                    ? score : String.join(",", decision.fraudCheck().reasons());
            fail(saga, t, Stage.FRAUD_CHECK, "RISK_DECLINED", reasons);
        }
    }

    /** Stages 6 and 7. A refused hold is the ledger saying no, most often INSUFFICIENT_FUNDS. */
    private void reserveFunds(SagaInstance saga, StartTransferRequest t) {
        LedgerClient.Hold hold;
        try {
            hold = ledger.placeHold(new LedgerClient.HoldRequest(t.transactionId(), t.referenceNo(),
                    t.sourceAccountId(), t.amount(), t.currency(), t.actorId()));
        } catch (DownstreamException e) {
            fail(saga, t, Stage.FUNDS_CHECK, e.refused() ? e.getErrorCode() : "LEDGER_UNAVAILABLE", null);
            return;
        }
        saga.setHoldId(hold.holdId());
        save(saga, SagaState.FUNDS_RESERVED);
        events.passed(t.transactionId(), t.referenceNo(), Stage.FUNDS_CHECK, null);
        events.passed(t.transactionId(), t.referenceNo(), Stage.AUTHORIZED, null);
    }

    /**
     * Stage 8. A refusal means nothing was posted, so the hold is released. No answer is
     * ambiguous: the capture may have succeeded, so the hold is never released on a timeout.
     * The call is repeated with the same idempotency key, and if the ledger stays silent the
     * saga is left at FUNDS_RESERVED for a later run.
     */
    private void capture(SagaInstance saga, StartTransferRequest t) {
        for (int attempt = 1;; attempt++) {
            try {
                ledger.capture(t.transactionId(), saga.getHoldId(),
                        new LedgerClient.CaptureRequest(t.destinationAccountId(), t.actorId()));
                break;
            } catch (DownstreamException e) {
                if (e.refused()) {
                    saga.setFailureCode(e.getErrorCode());
                    save(saga, SagaState.COMPENSATING);
                    return;
                }
                if (attempt >= captureAttempts || !pause()) {
                    log.error("Ledger did not answer capture of hold {} for transaction {} after {} attempts; "
                            + "saga left at FUNDS_RESERVED", saga.getHoldId(), t.transactionId(), attempt);
                    return;
                }
            }
        }
        save(saga, SagaState.POSTED);
        events.passed(t.transactionId(), t.referenceNo(), Stage.POSTED, null);
    }

    private void releaseHold(SagaInstance saga, StartTransferRequest t) {
        try {
            ledger.release(t.transactionId(), saga.getHoldId(), "CAPTURE_FAILED");
        } catch (DownstreamException e) {
            log.error("Hold {} for transaction {} was not released; saga left at COMPENSATING: {}",
                    saga.getHoldId(), t.transactionId(), e.getMessage());
            return;
        }
        save(saga, SagaState.COMPENSATED);
        events.failed(t.transactionId(), t.referenceNo(), Stage.POSTED, saga.getFailureCode(), "hold released");
    }

    /** Stage 9: the ledger's own event confirms the posting. */
    public void onLedgerTransferCompleted(UUID transactionId, String referenceNo) {
        (transactionId != null ? sagas.findByTransactionId(transactionId) : sagas.findByReferenceNo(referenceNo))
                .filter(saga -> saga.getState() == SagaState.POSTED || saga.getState() == SagaState.COMPLETED)
                .ifPresent(saga -> events.passed(saga.getTransactionId(), referenceNo, Stage.LEDGER_UPDATE, null));
    }

    /**
     * Stages 10 and 11. A notification can only follow a ledger event, so when it arrives
     * every post-posting event is accounted for and the saga is complete. If it never
     * arrives the saga stays POSTED with stage 10 pending; the money has moved.
     */
    public void onNotificationDispatched(UUID transactionId, String referenceNo) {
        (transactionId != null ? sagas.findByTransactionId(transactionId) : sagas.findByReferenceNo(referenceNo))
                .filter(saga -> saga.getState() == SagaState.POSTED)
                .ifPresent(saga -> {
                    save(saga, SagaState.COMPLETED);
                    events.passed(saga.getTransactionId(), referenceNo, Stage.NOTIFICATION, null);
                    events.passed(saga.getTransactionId(), referenceNo, Stage.RECONCILIATION, null);
                });
    }

    private void fail(SagaInstance saga, StartTransferRequest t, Stage stage, String failureCode, String detail) {
        saga.setFailureCode(failureCode);
        save(saga, SagaState.FAILED);
        events.failed(t.transactionId(), t.referenceNo(), stage, failureCode, detail);
    }

    private void save(SagaInstance saga, SagaState state) {
        saga.setState(state);
        sagas.saveAndFlush(saga);
    }

    private boolean pause() {
        try {
            Thread.sleep(captureBackoff);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
