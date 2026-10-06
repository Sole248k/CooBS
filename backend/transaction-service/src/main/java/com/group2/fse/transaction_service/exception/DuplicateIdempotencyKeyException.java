package com.group2.fse.transaction_service.exception;

/**
 * Thrown when an in-flight duplicate idempotency key is detected (HTTP 409 Conflict).
 * Reference: CONTRACTS.md §4.4 (CONCURRENT_TRANSACTION_IN_FLIGHT).
 */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String message) {
        super(message);
    }
}
