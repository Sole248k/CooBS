package com.group2.fse.orchestration_service.saga;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.group2.fse.orchestration_service.client.RiskClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tells the risk engine what became of a transfer (R2). The engine keeps the counters; the
 * saga is the one that knows how a transfer ended.
 *
 * Best effort, and never part of the outcome: the transfer or the reversal has already ended
 * when this is called. A report that does not arrive leaves a counter behind the truth (less
 * of today's limit left, or one reversal not counted), never different money, so it is logged
 * and the saga carries on. The engine takes each report once per transfer, so repeating one
 * changes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RiskOutcomes {

    private final RiskClient risk;

    /**
     * An approved transfer ended without posting: the amount it reserved goes back to the
     * customer's daily limit. For a transfer that was stopped before risk approved it, the
     * engine holds no reservation and does nothing.
     */
    void notPosted(UUID transactionId) {
        try {
            risk.reportOutcome(transactionId, RiskClient.FAILED, null);
        } catch (RuntimeException e) {
            log.warn("The daily-limit reservation of transaction {} was not released: {}",
                    transactionId, e.getMessage());
        }
    }

    /**
     * A posted transfer was reversed: it counts as one of the sender's reversals for the day.
     * Its reservation stays; a reversal is not a refund of the day's limit.
     */
    void reversed(UUID transactionId, Long customerId) {
        if (customerId == null) {
            // A seeded transfer or an inbound credit: there is no sending customer of ours to count it for.
            log.info("Reversal of transaction {} not reported to risk: the sender is not known", transactionId);
            return;
        }
        try {
            risk.reportOutcome(transactionId, RiskClient.REVERSED, customerId);
        } catch (RuntimeException e) {
            log.warn("The reversal of transaction {} was not counted for customer {}: {}",
                    transactionId, customerId, e.getMessage());
        }
    }
}
