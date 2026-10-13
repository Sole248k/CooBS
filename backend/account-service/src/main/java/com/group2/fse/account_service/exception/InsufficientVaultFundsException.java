package com.group2.fse.account_service.exception;

public class InsufficientVaultFundsException extends RuntimeException {
    public InsufficientVaultFundsException(String message) {
        super(message);
    }
}
