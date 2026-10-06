package com.group2.fse.customer_service.exception;

public class KycNotFoundException extends RuntimeException {
    public KycNotFoundException(String message) {
        super(message);
    }
}
