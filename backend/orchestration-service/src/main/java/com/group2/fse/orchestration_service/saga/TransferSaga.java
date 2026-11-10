package com.group2.fse.orchestration_service.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.orchestration_service.client.CustomerClient;
import com.group2.fse.orchestration_service.client.DownstreamException;
import com.group2.fse.orchestration_service.client.InterbankGatewayClient;
import com.group2.fse.orchestration_service.client.InternalApi;
import com.group2.fse.orchestration_service.client.LedgerClient;
import com.group2.fse.orchestration_service.client.RiskClient;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartTransferRequest;

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
 *    +--declined/unavailable      +--hold refused                +--capture refused--> COMPENSATING --released--> COMPENSATED
 *    v                            v
 *  FAILED                       FAILED
 * </pre>
 *
 * A ledger call that gets no answer never fails the saga, because the call may have taken
 * effect. The saga stays where it is and {@link SagaRecoveryRunner} runs it again. Only a
 * hold that is still unanswered after the retry window is given up: RISK_APPROVED goes to
 * COMPENSATING with the hold the ledger finally reports, and ends FAILED once it is released.
 *
 * A transfer for another bank takes the same road with one more step between the hold and
 * the capture: the partner bank is asked, through the inter-bank gateway, to credit its
 * customer. The money is reserved before the partner is asked and leaves the sender's
 * account only once the partner has accepted, against that partner's settlement account.
 *
 * <pre>
 * FUNDS_RESERVED --partner accepts--> capture as above
 *        |
 *        +--partner refuses--> COMPENSATING --released--> FAILED, INTERBANK_REJECTED
 *        +--no answer--------> stays FUNDS_RESERVED with the hold, asked again by the sweep
 * </pre>
 */
@Slf4j
@Service
public class TransferSaga {

    private static final String LEDGER_UNAVAILABLE = "LEDGER_UNAVAILABLE";
    private static final String INTERBANK_REJECTED = "INTERBANK_REJECTED";
    private static final String OWN_BANK_CODE = "COOBS";
    /** How often one run reloads the row after losing a race for it before leaving it to the sweep. */
    private static final int MAX_CONFLICTS = 3;

    private final SagaRepository sagas;
    private final CustomerClient customers;
    private final RiskClient risk;
    private final LedgerClient ledger;
    private final StageEvents events;
    private final RiskOutcomes riskOutcomes;
    private final InterbankGatewayClient interbank;
    private final int captureAttempts;
    private final Duration captureBackoff;
    private final Duration holdRetryWindow;
    private final ObjectMapper objectMapper;

    public TransferSaga(SagaRepository sagas, CustomerClient customers, RiskClient risk, LedgerClient ledger,
            StageEvents events, RiskOutcomes riskOutcomes, InterbankGatewayClient interbank,
            @Value("${app.saga.capture-attempts}") int captureAttempts,
            @Value("${app.saga.capture-backoff}") Duration captureBackoff,
            @Value("${app.saga.hold-retry-window:5m}") Duration holdRetryWindow,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.holdRetryWindow = holdRetryWindow;
        this.sagas = sagas;
        this.customers = customers;
        this.risk = risk;
        this.ledger = ledger;
        this.events = events;
        this.riskOutcomes = riskOutcomes;
        this.interbank = interbank;
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
                sagas.store(saga);
            }
            advance(saga, transfer);
        } catch (OptimisticLockingFailureException conflict) {
            log.error("Saga row for transaction {} kept changing under this run; left for the sweep",
                    transfer.transactionId());
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }

    /**
     * Takes the saga as far as it will go. A step whose save is rejected lost a race for the
     * row, most often to a reversal. The row is read again and the next step is decided from
     * what is saved there, so the other writer's state is never overwritten.
     */
    private void advance(SagaInstance saga, StartTransferRequest transfer) {
        // What the partner bank said in this run; a later run only has the failure code.
        String partnerDetail = null;
        for (int conflicts = 0;; conflicts++) {
            try {
                if (conflicts > 0) {
                    saga = reload(saga);
                }
                if (saga.getState() == SagaState.STARTED) {
                    checkRisk(saga, transfer);
                }
                if (saga.getState() == SagaState.RISK_APPROVED) {
                    reserveFunds(saga, transfer);
                }
                if (saga.getState() == SagaState.FUNDS_RESERVED) {
                    if (isOutboundInterbank(transfer)) {
                        partnerDetail = settleWithPartner(saga, transfer);
                    } else {
                        capture(saga, transfer, null);
                    }
                }
                if (saga.getState() == SagaState.COMPENSATING) {
                    releaseHold(saga, transfer, partnerDetail);
                }
                return;
            } catch (OptimisticLockingFailureException conflict) {
                if (conflicts >= MAX_CONFLICTS) {
                    throw conflict;
                }
                log.warn("Saga row for transaction {} was changed by someone else; deciding again from the saved row",
                        transfer.transactionId());
            }
        }
    }

    /**
     * The saved row, after a rejected save. One thing must not be lost with the stale copy:
     * a hold this run had just been given by the ledger when a reversal cancelled the
     * transfer. The cancelled row knows nothing of it, so it is recorded there and released.
     */
    private SagaInstance reload(SagaInstance stale) {
        SagaInstance saved = sagas.findByTransactionId(stale.getTransactionId()).orElseThrow();
        if (stale.getHoldId() != null && saved.getHoldId() == null && saved.getState() == SagaState.FAILED) {
            saved.setHoldId(stale.getHoldId());
            save(saved, SagaState.COMPENSATING);
        }
        return saved;
    }

    /** A missing bank code means this bank, as at T1; any other code makes the transfer outbound. */
    private static boolean isOutboundInterbank(StartTransferRequest t) {
        String bankCode = t.destinationBankCode();
        return bankCode != null && !bankCode.isBlank() && !OWN_BANK_CODE.equalsIgnoreCase(bankCode.trim());
    }

    /** Stages 4 and 5. Fails closed: anything other than a clear APPROVE ends the saga before any hold. */
    private void checkRisk(SagaInstance saga, StartTransferRequest t) {
        RiskClient.Decision decision;
        try {
            CustomerClient.RiskProfile profile = customers.riskProfile(t.customerId());
            // transaction-service says whether the customer has paid this account before (O1).
            decision = risk.evaluate(new RiskClient.Request(t.transactionId(), t.customerId(), t.amount(),
                    t.currency(), t.channel(), profile.riskScore(), profile.kycTier(), profile.dailyLimit(),
                    Boolean.TRUE.equals(t.isNewBeneficiary())));
        } catch (RuntimeException e) {
            log.warn("Risk check unavailable for transaction {}: {}", t.transactionId(), e.getMessage());
            fail(saga, t, Stage.FRAUD_CHECK, "RISK_UNAVAILABLE", null);
            // The engine may have approved and reserved the amount before its answer was lost.
            riskOutcomes.notPosted(t.transactionId());
            return;
        }

        saga.setRiskDecision(decision.decision());
        String score = decision.score() == null ? null : "score " + decision.score().toPlainString();
        boolean fraudFailed = decision.fraudCheck() != null && decision.fraudCheck().failed();
        boolean limitFailed = decision.limitCheck() != null && decision.limitCheck().failed();

        if (RiskClient.APPROVE.equals(decision.decision())) {
            try {
                save(saga, SagaState.RISK_APPROVED);
            } catch (OptimisticLockingFailureException cancelledMeanwhile) {
                // The engine has just reserved the amount for a transfer that a reversal ended
                // while it was deciding. Nothing will come after this to give it back.
                riskOutcomes.notPosted(t.transactionId());
                throw cancelledMeanwhile;
            }
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

    /**
     * Stages 6 and 7. A refused hold is the ledger saying no, most often INSUFFICIENT_FUNDS.
     * No answer is ambiguous: the hold may have been placed. The saga then stays at
     * RISK_APPROVED and the sweep repeats the call with the same reference, for which the
     * ledger returns the hold it already has.
     *
     * The repeats are bounded by time, because the row has nowhere to count them. Once the
     * saga has waited at RISK_APPROVED for longer than the retry window the transfer is given
     * up, but still not on a guess: the call is made once more to learn the hold, and only
     * the hold the ledger answers with is released before the saga fails.
     */
    private void reserveFunds(SagaInstance saga, StartTransferRequest t) {
        boolean givenUp = saga.getUpdatedAt() != null
                && saga.getUpdatedAt().isBefore(Instant.now().minus(holdRetryWindow));
        LedgerClient.Hold hold;
        try {
            hold = ledger.placeHold(new LedgerClient.HoldRequest(t.transactionId(), t.referenceNo(),
                    t.sourceAccountId(), t.amount(), t.currency(), t.actorId()));
        } catch (DownstreamException e) {
            if (e.refused()) {
                fail(saga, t, Stage.FUNDS_CHECK, e.getErrorCode(), null);
                riskOutcomes.notPosted(t.transactionId());
                return;
            }
            log.error("Ledger did not answer the hold for transaction {}; saga left at RISK_APPROVED "
                    + "for the sweep to repeat it: {}", t.transactionId(), e.getMessage());
            return;
        }
        saga.setHoldId(hold.holdId());
        if (givenUp) {
            saga.setFailureCode(LEDGER_UNAVAILABLE);
            save(saga, SagaState.COMPENSATING);
            return;
        }
        save(saga, SagaState.FUNDS_RESERVED);
        events.passed(t.transactionId(), t.referenceNo(), Stage.FUNDS_CHECK, null);
        if (!isOutboundInterbank(t)) {
            // A transfer to another bank is authorised by that bank's answer, not by the hold.
            events.passed(t.transactionId(), t.referenceNo(), Stage.AUTHORIZED, null);
        }
    }

    /**
     * Stage 7 of a transfer to another bank. The money is already reserved; the partner is now
     * asked to credit its customer, and only its answer decides what happens to the hold.
     *
     * Accepted: the hold is captured against the partner's settlement account, which is the
     * destinationAccountId transaction-service gave for this bank. Refused, by the partner or
     * by our own gateway before anything was sent: the hold is released and the transfer fails.
     * No answer: the partner may have credited its customer, so the hold is kept and the saga
     * stays at FUNDS_RESERVED; the sweep asks again, and the gateway sends one message per
     * transaction however often it is asked. The hold is never released on a timeout.
     *
     * @return what the partner said when it refused, for the stage event; otherwise null
     */
    private String settleWithPartner(SagaInstance saga, StartTransferRequest t) {
        String bank = t.destinationBankCode().trim();
        InterbankGatewayClient.Answer answer;
        try {
            answer = interbank.send(new InterbankGatewayClient.Dispatch(t.transactionId(), t.referenceNo(),
                    bank, t.amount(), t.currency()));
        } catch (DownstreamException e) {
            if (e.refused()) {
                // Our own gateway said no (the bank is not a configured partner, for one): nothing was sent.
                log.warn("Transaction {} was not sent to {}: {}", t.transactionId(), bank, e.getErrorCode());
                saga.setFailureCode(INTERBANK_REJECTED);
                save(saga, SagaState.COMPENSATING);
                return "not sent to " + bank + ": " + e.getErrorCode();
            }
            log.error("No answer about transaction {} for {}; the hold {} is kept and the saga left at "
                    + "FUNDS_RESERVED for the sweep to ask again: {}", t.transactionId(), bank, saga.getHoldId(),
                    e.getMessage());
            return null;
        }
        if (answer.accepted()) {
            String reference = answer.partnerReference() == null ? "" : ", reference " + answer.partnerReference();
            capture(saga, t, "accepted by " + bank + reference);
            return null;
        }
        if (answer.rejected()) {
            log.warn("{} refused transaction {}: {}", bank, t.transactionId(), answer.reasonCode());
            saga.setFailureCode(INTERBANK_REJECTED);
            save(saga, SagaState.COMPENSATING);
            return "refused by " + bank + (answer.reasonCode() == null ? "" : ": " + answer.reasonCode());
        }
        // PENDING, or a status this service does not know: the outcome at the partner is unknown.
        log.error("{} has not confirmed transaction {} ({}); the hold {} is kept for staff to follow up, and "
                + "the sweep asks again", bank, t.transactionId(), answer.partnerStatus(), saga.getHoldId());
        return null;
    }

    /**
     * Stage 8. A refusal means nothing was posted, so the hold is released. No answer is
     * ambiguous: the capture may have succeeded, so the hold is never released on a timeout.
     * The call is repeated with the same idempotency key, and if the ledger stays silent the
     * saga is left at FUNDS_RESERVED for a later run.
     *
     * A transfer to another bank is captured only after the partner has accepted it. If the
     * ledger then refuses, the partner already holds the money, so the hold is not released:
     * the saga stays at FUNDS_RESERVED and the refusal is for staff to resolve.
     *
     * @param authorizedBy for a transfer to another bank, what the partner answered; stage 7 is
     *                     published with it once the money has moved. Null for our own bank,
     *                     whose stage 7 was published when the hold was placed.
     */
    private void capture(SagaInstance saga, StartTransferRequest t, String authorizedBy) {
        for (int attempt = 1;; attempt++) {
            try {
                ledger.capture(t.transactionId(), saga.getHoldId(),
                        new LedgerClient.CaptureRequest(t.destinationAccountId(), t.actorId()));
                break;
            } catch (DownstreamException e) {
                if (e.refused() && authorizedBy != null) {
                    log.error("The ledger refused to capture hold {} for transaction {} ({}) after the partner "
                            + "bank accepted the transfer; the hold is kept and the saga left at FUNDS_RESERVED "
                            + "for staff", saga.getHoldId(), t.transactionId(), e.getErrorCode());
                    return;
                }
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
        if (authorizedBy != null) {
            events.passed(t.transactionId(), t.referenceNo(), Stage.AUTHORIZED, authorizedBy);
        }
        events.passed(t.transactionId(), t.referenceNo(), Stage.POSTED, null);
    }

    /**
     * Undoes a hold. After a refused capture the saga ends COMPENSATED at stage 8. A hold that
     * was given up or cancelled before it was ever authorised ends FAILED at stage 6, where
     * it stopped. A transfer the partner bank refused ends FAILED at stage 7, the stage that
     * bank's answer decides.
     */
    private void releaseHold(SagaInstance saga, StartTransferRequest t, String partnerDetail) {
        boolean neverAuthorized = LEDGER_UNAVAILABLE.equals(saga.getFailureCode())
                || ReversalSaga.CANCELLED_BEFORE_HOLD.equals(saga.getFailureCode());
        boolean partnerRefused = INTERBANK_REJECTED.equals(saga.getFailureCode());
        try {
            ledger.release(t.transactionId(), saga.getHoldId(),
                    neverAuthorized ? "HOLD_ABANDONED" : partnerRefused ? INTERBANK_REJECTED : "CAPTURE_FAILED");
        } catch (DownstreamException e) {
            log.error("Hold {} for transaction {} was not released; saga left at COMPENSATING: {}",
                    saga.getHoldId(), t.transactionId(), e.getMessage());
            return;
        }
        if (neverAuthorized) {
            save(saga, SagaState.FAILED);
            events.failed(t.transactionId(), t.referenceNo(), Stage.FUNDS_CHECK, saga.getFailureCode(),
                    "hold released");
            riskOutcomes.notPosted(t.transactionId());
            return;
        }
        if (partnerRefused) {
            save(saga, SagaState.FAILED);
            events.failed(t.transactionId(), t.referenceNo(), Stage.AUTHORIZED, INTERBANK_REJECTED,
                    partnerDetail == null ? "hold released" : partnerDetail + "; hold released");
            riskOutcomes.notPosted(t.transactionId());
            return;
        }
        save(saga, SagaState.COMPENSATED);
        events.failed(t.transactionId(), t.referenceNo(), Stage.POSTED, saga.getFailureCode(), "hold released");
        riskOutcomes.notPosted(t.transactionId());
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
        sagas.store(saga);
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
