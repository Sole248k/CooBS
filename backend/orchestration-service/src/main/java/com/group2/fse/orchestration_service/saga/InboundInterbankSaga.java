package com.group2.fse.orchestration_service.saga;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.group2.fse.orchestration_service.client.DownstreamException;
import com.group2.fse.orchestration_service.client.InternalApi;
import com.group2.fse.orchestration_service.client.LedgerClient;
import com.group2.fse.orchestration_service.dto.SagaDtos.InboundInterbankRequest;

import lombok.RequiredArgsConstructor;

/**
 * Inbound inter-bank credit (O4). No risk check and no hold: one posting through L8 that
 * debits the partner's settlement GL account and credits the beneficiary. Synchronous,
 * because the partner bank is waiting for the answer.
 */
@Service
@RequiredArgsConstructor
public class InboundInterbankSaga {

    private static final String NOT_APPLICABLE = "not applicable";

    private final SagaRepository sagas;
    private final LedgerClient ledger;
    private final StageEvents events;

    /**
     * @throws DownstreamException when the ledger gives no answer. The saga stays STARTED and
     *         the caller may repeat the call; the idempotency key makes that one posting.
     */
    public SagaInstance run(InboundInterbankRequest r, String correlationId) {
        MDC.put(InternalApi.CORRELATION_MDC_KEY, correlationId);
        try {
            SagaInstance saga = sagas.findOrCreate(r.transactionId());
            if (saga.getReferenceNo() == null && r.referenceNo() != null) {
                saga.setReferenceNo(r.referenceNo());
            }
            if (saga.getState() != SagaState.STARTED) {
                return saga;
            }

            String failureCode = post(r);
            saga.setFailureCode(failureCode);
            saga.setState(failureCode == null ? SagaState.COMPLETED : SagaState.FAILED);
            sagas.store(saga);

            for (Stage stage : Stage.values()) {
                if (stage.no() < Stage.POSTED.no()) {
                    events.passed(r.transactionId(), r.referenceNo(), stage,
                            stage.no() >= Stage.AUTHENTICATED.no() ? NOT_APPLICABLE : null);
                } else if (failureCode == null) {
                    events.passed(r.transactionId(), r.referenceNo(), stage, null);
                } else {
                    events.failed(r.transactionId(), r.referenceNo(), Stage.POSTED, failureCode, null);
                    break;
                }
            }
            return saga;
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }

    /** Returns null when posted, otherwise the ledger's error code. */
    private String post(InboundInterbankRequest r) {
        try {
            LedgerClient.PostingsResult result = ledger.post(new LedgerClient.PostingsRequest(r.transactionId(),
                    List.of(new LedgerClient.Posting(r.referenceNo(), r.settlementAccountId(),
                            r.destinationAccountId(), r.amount(), "Inter-bank credit from " + r.sourceBankCode()))));
            if (result.failed() == 0 && result.posted() == 1) {
                return null;
            }
            return result.results() == null || result.results().isEmpty()
                    || result.results().get(0).errorCode() == null
                            ? "INTERNAL_SERVER_ERROR" : result.results().get(0).errorCode();
        } catch (DownstreamException e) {
            if (!e.refused()) {
                throw e;
            }
            return e.getErrorCode();
        }
    }
}
