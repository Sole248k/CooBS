package com.group2.fse.orchestration_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.group2.fse.orchestration_service.saga.SagaInstance;
import com.group2.fse.orchestration_service.saga.SagaState;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request and response bodies of O1, O3 and O4, as in section 8 of the API contract. */
public final class SagaDtos {

    private SagaDtos() {
    }

    /** O1 request. */
    public record StartTransferRequest(
            @NotNull UUID transactionId,
            @NotBlank String referenceNo,
            @NotBlank String channel,
            @NotNull Long customerId,
            @NotNull Long sourceAccountId,
            @NotNull Long destinationAccountId,
            String destinationBankCode,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotNull Long actorId,
            String actorType,
            // Optional. A start that does not carry it is read as false.
            @JsonProperty("isNewBeneficiary") Boolean isNewBeneficiary) {
    }

    /** O1 response. */
    public record SagaStartedResponse(UUID transactionId, SagaState sagaState) {
    }

    /** O2 request. */
    public record StartReversalRequest(
            @NotNull UUID transactionId,
            @NotNull UUID reversesTransactionId,
            @NotBlank String referenceNo,
            @NotBlank String reason,
            @NotNull Long actorId) {
    }

    /** O3 response. */
    public record SagaStateResponse(
            UUID transactionId,
            SagaState sagaState,
            Long holdId,
            String riskDecision,
            String failureCode,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {

        public static SagaStateResponse of(SagaInstance saga) {
            return new SagaStateResponse(saga.getTransactionId(), saga.getState(), saga.getHoldId(),
                    saga.getRiskDecision(), saga.getFailureCode(), saga.getUpdatedAt());
        }
    }

    /** O4 request. */
    public record InboundInterbankRequest(
            @NotNull UUID transactionId,
            @NotBlank String referenceNo,
            String messageId,
            String sourceBankCode,
            @NotNull Long settlementAccountId,
            @NotNull Long destinationAccountId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency) {
    }

    /** O4 response. */
    public record InboundInterbankResponse(UUID transactionId, SagaState sagaState, String referenceNo) {
    }
}
