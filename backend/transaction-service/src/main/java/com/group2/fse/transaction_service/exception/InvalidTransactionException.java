package com.group2.fse.transaction_service.exception;

/**
 * Thrown when transfer pre-flight validation fails (HTTP 400 Bad Request).
 */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
