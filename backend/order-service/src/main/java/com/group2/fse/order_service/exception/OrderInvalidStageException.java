package com.group2.fse.order_service.exception;

public class OrderInvalidStageException extends RuntimeException {
    public OrderInvalidStageException(String message) {
        super(message);
    }
}
