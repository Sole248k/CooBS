package com.group2.fse.transaction_service.client;

/** account-service gave no usable answer: unreachable, timed out, or an error other than 404. */
public class AccountLookupUnavailableException extends RuntimeException {

    public AccountLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
