package com.group2.fse.orchestration_service.saga;

/**
 * The eleven stages, spelled as in section 2.7 of the API contract (H7). The ordinal is
 * the stage number minus one. This service publishes 4 to 11; 1 to 3 only for an inbound
 * inter-bank credit (O4).
 */
public enum Stage {
    INITIATED,
    VALIDATED,
    AUTHENTICATED,
    FRAUD_CHECK,
    LIMIT_CHECK,
    FUNDS_CHECK,
    AUTHORIZED,
    POSTED,
    LEDGER_UPDATE,
    NOTIFICATION,
    RECONCILIATION;

    public int no() {
        return ordinal() + 1;
    }
}
