package com.group2.fse.orchestration_service.client;

import lombok.Getter;

/**
 * A failed internal call. With an error code the service answered and refused, so nothing
 * happened there. Without one the outcome is unknown (timeout, connection error, 5xx):
 * the call may or may not have taken effect.
 */
@Getter
public class DownstreamException extends RuntimeException {

    private final String errorCode;

    public DownstreamException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public boolean refused() {
        return errorCode != null;
    }
}
