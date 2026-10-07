package com.group2.fse.risk_engine.exception;

/**
 * The engine cannot decide, for example because the velocity and daily-limit counters cannot
 * be read. Answered as 503 RISK_UNAVAILABLE; the saga then declines the transfer before any hold.
 */
public class RiskUnavailableException extends RuntimeException {

    public RiskUnavailableException(String message) {
        super(message);
    }
}
