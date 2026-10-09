package com.group2.fse.orchestration_service.saga;

import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
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

    static final String CANCELLED_BEFORE_HOLD = "CANCELLED_BEFORE_HOLD";
    static final String REVERSAL_NOT_ALLOWED = "REVERSAL_NOT_ALLOWED";
    private static final String OWN_BANK_CODE = "COOBS";
    private static final int MAX_CONFLICTS = 3;

    private final SagaRepository sagas;
    private final LedgerClient ledger;
    private final StageEvents events;
    private final RiskOutcomes riskOutcomes;
    private final ObjectMapper objectMapper;

    public ReversalSaga(SagaRepository sagas, LedgerClient ledger, StageEvents events, RiskOutcomes riskOutcomes,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.sagas = sagas;
        this.ledger = ledger;
        this.events = events;
        this.riskOutcomes = riskOutcomes;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Executes the reversal saga for request r.
     */
    public void run(StartReversalRequest r, String correlationId) {
        MDC.put(InternalApi.CORRELATION_MDC_KEY, correlationId);
        try {
            for (int conflicts = 0;; conflicts++) {
                try {
                    attempt(r);
                    return;
                } catch (OptimisticLockingFailureException conflict) {
                    if (conflicts >= MAX_CONFLICTS) {
                        log.error("Saga rows for reversal {} kept changing under this run; left for the sweep",
                                r.transactionId());
                        return;
                    }
                    log.warn("The transfer {} moved on while reversal {} was deciding; deciding again from the "
                            + "saved row", r.reversesTransactionId(), r.transactionId());
                }
            }
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }

    /**
     * One pass over the saved rows. A save that is rejected means the original transfer was
     * written by its own saga after it was read here; the pass is abandoned without
     * overwriting that, and {@link #run} starts another from what is saved now.
     */
    private void attempt(StartReversalRequest r) {
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
        sagas.store(reversalSaga);

        Optional<SagaInstance> originalOpt = sagas.findByTransactionId(r.reversesTransactionId());
        if (originalOpt.isEmpty()) {
            fail(reversalSaga, r, "TRANSACTION_NOT_FOUND", "Original transaction not found: " + r.reversesTransactionId());
            return;
        }

        SagaInstance original = originalOpt.get();

        // Money that has gone, or may have gone, to another bank cannot be taken back from here.
        // Releasing the hold of a transfer the partner is still deciding on, or reversing the
        // postings of one it accepted, would leave the partner's customer credited and nothing
        // debited. Such money comes back only as a transfer from that bank.
        boolean sentOrSending = original.getState() == SagaState.FUNDS_RESERVED
                || original.getState() == SagaState.POSTED || original.getState() == SagaState.COMPLETED;
        if (sentOrSending && isOutboundInterbank(original)) {
            fail(reversalSaga, r, REVERSAL_NOT_ALLOWED,
                    "The original transfer went to another bank; it is returned by that bank, not reversed here");
            return;
        }

        if (original.getState() == SagaState.POSTED || original.getState() == SagaState.COMPLETED) {
            reversePostedTransfer(reversalSaga, original, r);
        } else if (original.getState() == SagaState.FUNDS_RESERVED) {
            cancelHeldTransfer(reversalSaga, original, r);
        } else if (original.getState() == SagaState.STARTED || original.getState() == SagaState.RISK_APPROVED) {
            cancelUnreservedTransfer(reversalSaga, original, r);
        } else {
            fail(reversalSaga, r, "ALREADY_TERMINAL", "Original transaction is already in terminal state " + original.getState());
        }
    }

    private void reversePostedTransfer(SagaInstance reversalSaga, SagaInstance original, StartReversalRequest r) {
        String origRef = original.getReferenceNo() != null ? original.getReferenceNo() : "UNKNOWN";
        try {
            ledger.reverse(r.transactionId(), new LedgerClient.ReversalRequest(
                    origRef, r.referenceNo(), r.reason(), r.actorId()));

            reversalSaga.setState(SagaState.COMPLETED);
            sagas.store(reversalSaga);

            // Publish reversal stage events
            events.passed(r.transactionId(), r.referenceNo(), Stage.POSTED, "Reversal posted for " + origRef);
            events.passed(r.transactionId(), r.referenceNo(), Stage.LEDGER_UPDATE, null);
            events.passed(r.transactionId(), r.referenceNo(), Stage.NOTIFICATION, null);
            events.passed(r.transactionId(), r.referenceNo(), Stage.RECONCILIATION, null);
            // The engine counts reversals per customer and day; the limit the transfer used stays used.
            riskOutcomes.reversed(original.getTransactionId(), senderOf(original));
        } catch (DownstreamException e) {
            if (!e.refused()) {
                // No answer is not a refusal: the ledger may have posted the reversal. The saga
                // stays at STARTED and the sweep repeats the call with the same references, for
                // which the ledger returns the reversal it already has.
                log.error("Ledger did not answer reversal {}; saga left at STARTED for the sweep to repeat it: {}",
                        r.transactionId(), e.getMessage());
                return;
            }
            log.error("Ledger refused reversal {}: {}", r.transactionId(), e.getMessage());
            fail(reversalSaga, r, e.getErrorCode(), e.getMessage());
        }
    }

    private void cancelHeldTransfer(SagaInstance reversalSaga, SagaInstance original, StartReversalRequest r) {
        try {
            ledger.release(original.getTransactionId(), original.getHoldId(), "REVERSAL_REQUESTED: " + r.reason());

            original.setState(SagaState.COMPENSATED);
            sagas.store(original);

            reversalSaga.setState(SagaState.COMPLETED);
            sagas.store(reversalSaga);

            events.failed(original.getTransactionId(), original.getReferenceNo(), Stage.POSTED, "REVERSED", "Hold released due to reversal");
            events.passed(r.transactionId(), r.referenceNo(), Stage.POSTED, "Hold cancelled and funds released");
            events.passed(r.transactionId(), r.referenceNo(), Stage.NOTIFICATION, null);
            // The transfer never posted, so its share of the daily limit goes back.
            riskOutcomes.notPosted(original.getTransactionId());
        } catch (DownstreamException e) {
            if (!e.refused()) {
                // The release may have been applied. Both rows are left as they are for the sweep.
                log.error("Ledger did not answer the release of hold {} for reversal {}; saga left at STARTED "
                        + "for the sweep to repeat it: {}", original.getHoldId(), r.transactionId(), e.getMessage());
                return;
            }
            log.error("Ledger refused to release hold {} for reversal {}: {}",
                    original.getHoldId(), r.transactionId(), e.getMessage());
            fail(reversalSaga, r, e.getErrorCode(), e.getMessage());
        }
    }

    private void cancelUnreservedTransfer(SagaInstance reversalSaga, SagaInstance original, StartReversalRequest r) {
        original.setState(SagaState.FAILED);
        original.setFailureCode(CANCELLED_BEFORE_HOLD);
        sagas.store(original);

        reversalSaga.setState(SagaState.COMPLETED);
        sagas.store(reversalSaga);

        events.passed(r.transactionId(), r.referenceNo(), Stage.POSTED, "Pre-hold transaction cancelled");
        events.passed(r.transactionId(), r.referenceNo(), Stage.NOTIFICATION, null);
        // If risk had approved it, the amount it reserved goes back; if not, the engine has nothing to release.
        riskOutcomes.notPosted(original.getTransactionId());
    }

    /** True when the original transfer was for another bank, read from the payload its saga was started with. */
    private boolean isOutboundInterbank(SagaInstance original) {
        if (original.getPayload() == null) {
            return false;
        }
        try {
            String bankCode = objectMapper.readTree(original.getPayload()).path("destinationBankCode").asText("");
            return !bankCode.isBlank() && !OWN_BANK_CODE.equalsIgnoreCase(bankCode.trim());
        } catch (Exception e) {
            log.warn("The payload of transaction {} could not be read for its bank: {}",
                    original.getTransactionId(), e.getMessage());
            return false;
        }
    }

    /**
     * The customer who sent the original transfer, read from the payload its saga was started
     * with. Null for a row with no payload (seeded, or an inbound credit from another bank).
     */
    private Long senderOf(SagaInstance original) {
        if (original.getPayload() == null) {
            return null;
        }
        try {
            JsonNode customerId = objectMapper.readTree(original.getPayload()).path("customerId");
            return customerId.isIntegralNumber() ? customerId.longValue() : null;
        } catch (Exception e) {
            log.warn("The payload of transaction {} could not be read for its sender: {}",
                    original.getTransactionId(), e.getMessage());
            return null;
        }
    }

    private void fail(SagaInstance reversalSaga, StartReversalRequest r, String failureCode, String detail) {
        reversalSaga.setFailureCode(failureCode);
        reversalSaga.setState(SagaState.FAILED);
        sagas.store(reversalSaga);
        events.failed(r.transactionId(), r.referenceNo(), Stage.POSTED, failureCode, detail);
    }
}
