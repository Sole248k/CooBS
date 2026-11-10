package com.group2.fse.customer_service.exception;

public class KycRequestNotFoundException extends RuntimeException {
    public KycRequestNotFoundException(String message) {
        super(message);
    }
}
