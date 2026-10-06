package com.group2.fse.ledger_service.exception;

import lombok.Getter;

/**
 * Thrown when an original transaction has already been reversed.
 * Error Code: ALREADY_REVERSED / DUPLICATE_REVERSAL (HTTP 409 Conflict).
 */
@Getter
public class TransactionAlreadyReversedException extends RuntimeException {

    private final String referenceNo;

    public TransactionAlreadyReversedException(String referenceNo) {
        super(String.format("Transaction with reference %s has already been reversed", referenceNo));
        this.referenceNo = referenceNo;
    }
}
