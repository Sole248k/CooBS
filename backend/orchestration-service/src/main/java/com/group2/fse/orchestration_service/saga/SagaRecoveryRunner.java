package com.group2.fse.orchestration_service.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.dto.SagaDtos.StartReversalRequest;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartTransferRequest;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Picks up sagas left in flight: once at startup, then on a schedule. A saga whose row
 * stopped short of an end state is run again from its saved payload; {@code run} continues
 * from the saved state and the ledger's idempotency keys make the repeated call safe. A hold
 * is never released here on a guess: FUNDS_RESERVED retries the capture, exactly as a live
 * saga would.
 *
 * POSTED is not swept. It waits on events, not on a call this service makes.
 */
@Slf4j
@Component
public class SagaRecoveryRunner implements ApplicationRunner {

    private static final List<SagaState> IN_FLIGHT = List.of(
            SagaState.STARTED, SagaState.RISK_APPROVED, SagaState.FUNDS_RESERVED, SagaState.COMPENSATING);

    private final SagaRepository sagas;
    private final TransferSaga transferSaga;
    private final ReversalSaga reversalSaga;
    private final JsonMapper jsonMapper;
    private final AsyncTaskExecutor applicationTaskExecutor;
    private final Duration staleAfter;

    public SagaRecoveryRunner(SagaRepository sagas, TransferSaga transferSaga, ReversalSaga reversalSaga,
            JsonMapper jsonMapper, AsyncTaskExecutor applicationTaskExecutor,
            @Value("${app.saga.sweep-stale-after}") Duration staleAfter) {
        this.sagas = sagas;
        this.transferSaga = transferSaga;
        this.reversalSaga = reversalSaga;
        this.jsonMapper = jsonMapper;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.staleAfter = staleAfter;
    }

    // ponytail: the startup sweep takes every in-flight row, so a second instance starting
    // while the first is mid-saga repeats that saga's step. Idempotency keys keep the ledger
    // correct; add a row claim if the service is scaled past one instance.
    @Override
    public void run(ApplicationArguments args) {
        resumeAll(sagas.findByStateIn(IN_FLIGHT));
    }

    /**
     * The scheduled sweep. A row that changed within {@code app.saga.sweep-stale-after} is left
     * alone, because a live thread is probably still working on it. A saga parked at its stage
     * does not change its row, so it is tried again on every sweep until the ledger answers.
     */
    @Scheduled(initialDelayString = "${app.saga.sweep-interval-ms}", fixedDelayString = "${app.saga.sweep-interval-ms}")
    public void sweep() {
        sweepOlderThan(Instant.now().minus(staleAfter));
    }

    void sweepOlderThan(Instant cutoff) {
        resumeAll(sagas.findByStateInAndUpdatedAtBefore(IN_FLIGHT, cutoff));
    }

    private void resumeAll(List<SagaInstance> stuck) {
        if (!stuck.isEmpty()) {
            log.info("Recovering {} saga(s) left in flight", stuck.size());
        }
        resumable.forEach(this::resume);
    }

    private void resume(SagaInstance saga) {
        // The original correlation ID is not stored, so the resumed run gets its own.
        String correlationId = UUID.randomUUID().toString();
        try {
            // Read straight into the request type so that the amount keeps its scale: the
            // repeated ledger call has to carry exactly what the first one did.
            if (jsonMapper.readTree(saga.getPayload()).has("reversesTransactionId")) {
                StartReversalRequest reversal = jsonMapper.readValue(saga.getPayload(), StartReversalRequest.class);
                applicationTaskExecutor.execute(() -> reversalSaga.run(reversal, correlationId));
            } else {
                StartTransferRequest transfer = jsonMapper.readValue(saga.getPayload(), StartTransferRequest.class);
                applicationTaskExecutor.execute(() -> transferSaga.run(transfer, correlationId));
            }
        } catch (RuntimeException e) {
            log.error("Saga for transaction {} could not be recovered from its payload: {}",
                    saga.getTransactionId(), e.getMessage());
        }
    }
}
