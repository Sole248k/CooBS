package com.group2.fse.orchestration_service.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.dto.SagaDtos.StartReversalRequest;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartTransferRequest;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Picks up sagas left in flight, once at startup and then on a schedule. A saga whose row
 * stopped short of an end state (the service went down, the ledger gave no answer) is run
 * again from its saved payload; {@code run} continues from the saved state and the ledger's
 * idempotency keys make the repeated call safe. A hold is never released here on a guess:
 * FUNDS_RESERVED retries the capture, exactly as a live saga would.
 *
 * POSTED is not swept. It waits on events, not on a call this service makes.
 */
@Slf4j
@Component
@EnableScheduling
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
            @Value("${app.saga.sweep-stale-after:60s}") Duration staleAfter) {
        this.sagas = sagas;
        this.transferSaga = transferSaga;
        this.reversalSaga = reversalSaga;
        this.jsonMapper = jsonMapper;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.staleAfter = staleAfter;
    }

    // ponytail: the startup run takes every in-flight row whatever its age, so a second
    // instance starting while the first is mid-saga repeats that saga's step. Idempotency
    // keys keep the ledger correct; add a row claim if the service is scaled past one instance.
    @Override
    public void run(ApplicationArguments args) {
        resume(sagas.findByStateIn(IN_FLIGHT), true);
    }

    /**
     * The scheduled sweep. It takes only rows that have not been written for longer than the
     * cutoff, which must stay above the longest a live saga sits between two writes (the
     * capture retries), so that it does not run a saga another thread is still working on.
     */
    @Scheduled(fixedDelayString = "${app.saga.sweep-interval:30s}",
            initialDelayString = "${app.saga.sweep-interval:30s}")
    public void sweep() {
        resume(sagas.findByStateInAndUpdatedAtBefore(IN_FLIGHT, Instant.now().minus(staleAfter)), false);
    }

    private void resume(List<SagaInstance> stuck, boolean startup) {
        // An inbound inter-bank saga stores no payload; the partner bank repeats that call itself.
        List<SagaInstance> resumable = stuck.stream().filter(saga -> saga.getPayload() != null).toList();
        if (startup) {
            stuck.stream().filter(saga -> saga.getPayload() == null).forEach(saga -> log.warn(
                    "Saga for transaction {} is at {} with no payload; not recovered",
                    saga.getTransactionId(), saga.getState()));
        }
        if (!resumable.isEmpty()) {
            log.info("Recovering {} saga(s) left in flight", resumable.size());
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
