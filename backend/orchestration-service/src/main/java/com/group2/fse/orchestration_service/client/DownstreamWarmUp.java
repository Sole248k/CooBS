package com.group2.fse.orchestration_service.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Makes the first real transfer after a start as fast as the hundredth.
 *
 * The risk call has a 150 ms budget and a timeout is a decline (RISK_UNAVAILABLE). The first
 * call on a cold stack is far slower than that: connections are opened, classes are loaded and
 * the other service runs its request path for the first time. Without this, the first transfer
 * after a start was declined although nothing was wrong.
 *
 * After start-up this calls the two services the risk stage needs, C7 and R1, until both
 * answer inside their budgets, and then a few times more. Neither call changes anything:
 * customer 0 does not exist, and the R1 request carries a daily limit of zero, so the engine
 * declines it and records nothing.
 *
 * ponytail: this warms the stack once, when this service starts. If risk-engine or
 * customer-service is restarted on its own later, its first call is cold again.
 */
@Slf4j
@Component
public class DownstreamWarmUp {

    private static final Long NO_CUSTOMER = 0L;
    private static final int EXTRA_ROUNDS = 3;

    private final CustomerClient customers;
    private final RiskClient risk;
    private final boolean enabled;
    private final int maxAttempts;
    private final Duration retryDelay;

    public DownstreamWarmUp(CustomerClient customers, RiskClient risk,
            @Value("${app.warm-up.enabled:true}") boolean enabled,
            @Value("${app.warm-up.max-attempts:36}") int maxAttempts,
            @Value("${app.warm-up.retry-delay:5s}") Duration retryDelay) {
        this.customers = customers;
        this.risk = risk;
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            return;
        }
        Thread thread = new Thread(this::warmUp, "downstream-warm-up");
        thread.setDaemon(true);
        thread.start();
    }

    /** Runs until one round answers in time, then a few more rounds, or gives up after maxAttempts. */
    void warmUp() {
        MDC.put(InternalApi.CORRELATION_MDC_KEY, "warm-up");
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (round()) {
                    for (int extra = 0; extra < EXTRA_ROUNDS; extra++) {
                        round();
                    }
                    log.info("Risk stage warmed up after {} attempt(s)", attempt);
                    return;
                }
                Thread.sleep(retryDelay.toMillis());
            }
            log.warn("Risk stage did not answer in time during warm-up; the first transfer may be declined "
                    + "with RISK_UNAVAILABLE");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }

    /** One C7 call and one R1 call. True when both services answered inside their budgets. */
    boolean round() {
        return answered(() -> customers.riskProfile(NO_CUSTOMER))
                & answered(() -> risk.evaluate(new RiskClient.Request(UUID.randomUUID(), NO_CUSTOMER,
                        new BigDecimal("0.01"), "PHP", "APP", BigDecimal.ZERO, "TIER_3_FULL",
                        new BigDecimal("0.00"), false)));
    }

    /** A refusal, such as 404 for customer 0, is an answer: the path ran end to end. */
    private static boolean answered(Runnable call) {
        try {
            call.run();
            return true;
        } catch (DownstreamException e) {
            return e.refused();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
