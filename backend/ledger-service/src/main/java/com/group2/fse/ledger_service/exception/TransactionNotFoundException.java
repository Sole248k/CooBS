package com.group2.fse.ledger_service.exception;

import lombok.Getter;

/**
 * Thrown when an original transaction cannot be located by reference number.
 * Error Code: TRANSACTION_NOT_FOUND (HTTP 404).
 */
@Getter
public class TransactionNotFoundException extends RuntimeException {

    private final String referenceNo;

    public TransactionNotFoundException(String referenceNo) {
        super(String.format("Original transaction not found for reference: %s", referenceNo));
        this.referenceNo = referenceNo;
    }
}
