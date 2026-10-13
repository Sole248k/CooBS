package com.group2.fse.account_service.exception;

public class VaultNotFoundException extends RuntimeException {
    public VaultNotFoundException(String message) {
        super(message);
    }
}
