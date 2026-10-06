package com.group2.fse.orchestration_service.saga;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.dto.SagaDtos.StartReversalRequest;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartTransferRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Startup sweep. A saga whose row stopped short of an end state when the service went down
 * is run again from its saved payload; {@code run} continues from the saved state and the
 * ledger's idempotency keys make the repeated call safe. A hold is never released here on
 * a guess: FUNDS_RESERVED retries the capture, exactly as a live saga would.
 *
 * POSTED is not swept. It waits on events, not on a call this service makes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRecoveryRunner implements ApplicationRunner {

    private static final List<SagaState> IN_FLIGHT = List.of(
            SagaState.STARTED, SagaState.RISK_APPROVED, SagaState.FUNDS_RESERVED, SagaState.COMPENSATING);

    private final SagaRepository sagas;
    private final TransferSaga transferSaga;
    private final ReversalSaga reversalSaga;
    private final JsonMapper jsonMapper;
    private final AsyncTaskExecutor applicationTaskExecutor;

    // ponytail: sweeps every in-flight row, so a second instance starting while the first is
    // mid-saga repeats that saga's step. Idempotency keys keep the ledger correct; add an
    // updated_at cutoff or a row claim if the service is scaled past one instance.
    @Override
    public void run(ApplicationArguments args) {
        List<SagaInstance> stuck = sagas.findByStateIn(IN_FLIGHT);
        if (!stuck.isEmpty()) {
            log.info("Recovering {} saga(s) left in flight", stuck.size());
        }
        stuck.forEach(this::resume);
    }

    private void resume(SagaInstance saga) {
        // An inbound inter-bank saga stores no payload; the partner bank repeats that call itself.
        if (saga.getPayload() == null) {
            log.warn("Saga for transaction {} is at {} with no payload; not recovered",
                    saga.getTransactionId(), saga.getState());
            return;
        }
        // The original correlation ID is not stored, so the resumed run gets its own.
        String correlationId = UUID.randomUUID().toString();
        try {
            JsonNode payload = jsonMapper.readTree(saga.getPayload());
            if (payload.has("reversesTransactionId")) {
                StartReversalRequest reversal = jsonMapper.treeToValue(payload, StartReversalRequest.class);
                applicationTaskExecutor.execute(() -> reversalSaga.run(reversal, correlationId));
            } else {
                StartTransferRequest transfer = jsonMapper.treeToValue(payload, StartTransferRequest.class);
                applicationTaskExecutor.execute(() -> transferSaga.run(transfer, correlationId));
            }
        } catch (RuntimeException e) {
            log.error("Saga for transaction {} could not be recovered from its payload: {}",
                    saga.getTransactionId(), e.getMessage());
        }
    }
}
