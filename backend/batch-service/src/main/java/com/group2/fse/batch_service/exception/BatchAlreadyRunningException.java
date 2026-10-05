package com.group2.fse.batch_service.exception;

public class BatchAlreadyRunningException extends RuntimeException {
    public BatchAlreadyRunningException(String message) {
        super(message);
    }
}
