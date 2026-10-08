package com.group2.fse.ledger_service.exception;

import lombok.Getter;

/**
 * Thrown when a hold that was already captured or released is asked to change again.
 * Error Code: HOLD_NOT_RESERVED (HTTP 409).
 */
@Getter
public class HoldNotReservedException extends RuntimeException {

    private final Long holdId;

    public HoldNotReservedException(Long holdId, String status) {
        super(String.format("Hold %d is not RESERVED (current status: %s)", holdId, status));
        this.holdId = holdId;
    }
}
