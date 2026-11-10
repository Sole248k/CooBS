package com.group2.fse.transaction_service.interbank.security;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InterbankAuthException extends RuntimeException {

    private final String reasonCode;
    private final HttpStatus httpStatus;

    public InterbankAuthException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
        this.httpStatus = HttpStatus.UNAUTHORIZED;
    }

    public InterbankAuthException(String reasonCode, String message, HttpStatus httpStatus) {
        super(message);
        this.reasonCode = reasonCode;
        this.httpStatus = httpStatus;
    }
}
