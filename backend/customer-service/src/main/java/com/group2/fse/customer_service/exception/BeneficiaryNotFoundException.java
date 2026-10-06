package com.group2.fse.customer_service.exception;

public class BeneficiaryNotFoundException extends RuntimeException {
    public BeneficiaryNotFoundException(String message) {
        super(message);
    }
}
