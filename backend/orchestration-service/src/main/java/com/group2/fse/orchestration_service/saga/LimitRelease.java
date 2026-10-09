package com.group2.fse.orchestration_service.saga;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.client.RiskClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tells the risk engine that a transfer it approved will not post (R2), so that the amount it
 * reserved goes back to the customer's daily limit.
 *
 * Best effort, and never part of the outcome: the transfer has already ended when this is
 * called. A release that does not arrive leaves the customer with less of today's limit, never
 * with different money, so it is logged and the saga carries on. The engine ignores a release
 * for a transfer it holds no reservation for, so calling it twice, or for a transfer that was
 * stopped before risk approved it, changes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LimitRelease {

    private final RiskClient risk;

    void release(UUID transactionId) {
        try {
            risk.reportOutcome(transactionId, RiskClient.FAILED);
        } catch (RuntimeException e) {
            log.warn("The daily-limit reservation of transaction {} was not released: {}",
                    transactionId, e.getMessage());
        }
    }
}
