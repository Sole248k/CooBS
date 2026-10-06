package com.group2.fse.orchestration_service.controller;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.group2.fse.orchestration_service.client.InternalApi;
import com.group2.fse.orchestration_service.dto.SagaDtos.InboundInterbankRequest;
import com.group2.fse.orchestration_service.dto.SagaDtos.InboundInterbankResponse;
import com.group2.fse.orchestration_service.dto.SagaDtos.SagaStartedResponse;
import com.group2.fse.orchestration_service.dto.SagaDtos.SagaStateResponse;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartReversalRequest;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartTransferRequest;
import com.group2.fse.orchestration_service.saga.InboundInterbankSaga;
import com.group2.fse.orchestration_service.saga.ReversalSaga;
import com.group2.fse.orchestration_service.saga.SagaInstance;
import com.group2.fse.orchestration_service.saga.SagaRepository;
import com.group2.fse.orchestration_service.saga.TransferSaga;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** O1, O2, O3 and O4. Internal only: a service token and nothing else (contract section 2.3). */
@RestController
@RequestMapping("/internal/v1/sagas")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class SagaInternalController {

    private final SagaRepository sagas;
    private final TransferSaga transferSaga;
    private final ReversalSaga reversalSaga;
    private final InboundInterbankSaga inboundInterbankSaga;
    private final AsyncTaskExecutor applicationTaskExecutor;
    private final JsonMapper jsonMapper;

    /** O1. Saves the saga with its payload, answers 202 and runs it in the background; progress is the stage events. */
    @PostMapping("/transfers")
    public ResponseEntity<SagaStartedResponse> startTransfer(
            @Valid @RequestBody StartTransferRequest request,
            @RequestHeader(value = InternalApi.CORRELATION_HEADER, required = false) String correlationId) {
        SagaInstance saga = sagas.findOrCreate(request.transactionId(), request.referenceNo(),
                jsonMapper.writeValueAsString(request));
        String correlation = correlationOrNew(correlationId);
        applicationTaskExecutor.execute(() -> transferSaga.run(request, correlation));
        return ResponseEntity.accepted().body(new SagaStartedResponse(saga.getTransactionId(), saga.getState()));
    }

    /** O2. Saves the reversal saga with its payload, answers 202 and runs it in the background. */
    @PostMapping("/reversals")
    public ResponseEntity<SagaStartedResponse> startReversal(
            @Valid @RequestBody StartReversalRequest request,
            @RequestHeader(value = InternalApi.CORRELATION_HEADER, required = false) String correlationId) {
        SagaInstance saga = sagas.findOrCreate(request.transactionId(), request.referenceNo(),
                jsonMapper.writeValueAsString(request));
        String correlation = correlationOrNew(correlationId);
        applicationTaskExecutor.execute(() -> reversalSaga.run(request, correlation));
        return ResponseEntity.accepted().body(new SagaStartedResponse(saga.getTransactionId(), saga.getState()));
    }

    /** O3. */
    @GetMapping("/{transactionId}")
    public SagaStateResponse sagaState(@PathVariable UUID transactionId) {
        return sagas.findByTransactionId(transactionId)
                .map(SagaStateResponse::of)
                .orElseThrow(() -> new NoSuchElementException("No saga for transaction " + transactionId));
    }

    /** O4. Synchronous. */
    @PostMapping("/inbound-interbank")
    public InboundInterbankResponse inboundInterbank(
            @Valid @RequestBody InboundInterbankRequest request,
            @RequestHeader(value = InternalApi.CORRELATION_HEADER, required = false) String correlationId) {
        SagaInstance saga = inboundInterbankSaga.run(request, correlationOrNew(correlationId));
        return new InboundInterbankResponse(saga.getTransactionId(), saga.getState(), request.referenceNo());
    }

    private static String correlationOrNew(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
