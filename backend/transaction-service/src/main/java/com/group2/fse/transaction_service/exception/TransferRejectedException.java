package com.group2.fse.transaction_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A transfer request that is refused before any saga starts. It carries the HTTP status and
 * the contract error code (section 2.5) the client switches on.
 */
@Getter
public class TransferRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String title;

    public TransferRejectedException(HttpStatus status, String errorCode, String title, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.title = title;
    }

    public static TransferRejectedException accountNotFound(String which) {
        return new TransferRejectedException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account Not Found",
                which + " account was not found.");
    }

    public static TransferRejectedException accountNotOwned() {
        return new TransferRejectedException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_OWNED", "Account Not Owned",
                "The source account does not belong to the signed-in customer.");
    }

    public static TransferRejectedException accountUnavailable() {
        return new TransferRejectedException(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_UNAVAILABLE",
                "Account Lookup Unavailable", "Accounts cannot be checked right now. No transfer was started.");
    }

    public static TransferRejectedException keyReused() {
        return new TransferRejectedException(HttpStatus.CONFLICT, "DUPLICATE_IDEMPOTENCY_KEY",
                "Idempotency Key Already Used", "This Idempotency-Key was already used for a different transfer.");
    }

    public static TransferRejectedException interbankNotAvailable() {
        return new TransferRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "INTERBANK_REJECTED",
                "Inter-bank Transfer Rejected", "Transfers to another bank are not available.");
    }
}
