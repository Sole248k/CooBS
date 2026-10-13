package com.group2.fse.account_service.exception;

public class InsufficientAccountFundsException extends RuntimeException {
    public InsufficientAccountFundsException(String message) {
        super(message);
    }
}
