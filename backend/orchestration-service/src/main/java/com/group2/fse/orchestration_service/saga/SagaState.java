package com.group2.fse.orchestration_service.saga;

/** Saga states, spelled as in section 8 of the API contract. */
public enum SagaState {
    STARTED,
    RISK_APPROVED,
    FUNDS_RESERVED,
    POSTED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}
