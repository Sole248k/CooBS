package com.group2.fse.ledger_service.exception;

import lombok.Getter;

/**
 * Thrown when a hold cannot be located.
 * Error Code: HOLD_NOT_FOUND (HTTP 404).
 */
@Getter
public class HoldNotFoundException extends RuntimeException {

    private final Long holdId;

    public HoldNotFoundException(Long holdId) {
        super(String.format("Hold %d not found", holdId));
        this.holdId = holdId;
    }

    public HoldNotFoundException(String message) {
        super(message);
        this.holdId = null;
    }
}
