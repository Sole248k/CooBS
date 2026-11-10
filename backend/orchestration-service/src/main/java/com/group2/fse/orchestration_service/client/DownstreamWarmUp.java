package com.group2.fse.orchestration_service.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
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
 * answer inside their budgets, and then a few times more. Both calls are for customer 0, who
 * does not exist, so no member is touched.
 *
 * The R1 request is one the engine approves, each time with a new transaction id. An approval
 * is the only answer after which the engine writes its counters, and that write is most of the
 * work in a real transfer: a request the engine declines left it cold, and the first transfer
 * was still declined. What it leaves behind are the engine's counters for customer 0, which
 * expire on their own.
 *
 * A warm stack does not stay warm. With no transfers the connections are closed and the first
 * call after a long quiet spell ran past 150 ms again, so once the start-up warm-up has finished
 * one more round is sent on a short interval for as long as this service runs. The same rounds
 * warm risk-engine or customer-service again after either is restarted on its own.
 */
@Slf4j
@Component
public class DownstreamWarmUp {

    private static final Long NO_CUSTOMER = 0L;
    private static final int EXTRA_ROUNDS = 3;
    private static final BigDecimal WARM_UP_AMOUNT = new BigDecimal("0.01");
    // Far above anything the warm-up can add up to in a day, so the request stays approved.
    private static final BigDecimal WARM_UP_DAILY_LIMIT = new BigDecimal("1000000.00");

    private final CustomerClient customers;
    private final RiskClient risk;
    private final boolean enabled;
    private final int maxAttempts;
    private final Duration retryDelay;
    private volatile boolean startUpFinished;
    // Read and written by the scheduler thread only.
    private boolean answering = true;

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
            startUpFinished = true;
        }
    }

    /**
     * One round on every interval, so that an idle stack does not go cold. It waits for the
     * start-up warm-up to finish, whether that succeeded or gave up. The interval stays under
     * the 30 seconds after which the JDK HTTP client closes an idle connection. A round that
     * gets no answer changes nothing here: the next one is sent on the next interval.
     */
    @Scheduled(fixedDelayString = "${app.warm-up.keep-warm-interval:20s}",
            initialDelayString = "${app.warm-up.keep-warm-interval:20s}")
    public void keepWarm() {
        if (!enabled || !startUpFinished) {
            return;
        }
        MDC.put(InternalApi.CORRELATION_MDC_KEY, "warm-up");
        try {
            boolean answered = round();
            if (answered != answering) {
                if (answered) {
                    log.info("Risk stage is answering in time again");
                } else {
                    log.warn("Risk stage did not answer in time on a keep-warm round; a transfer now may be "
                            + "declined with RISK_UNAVAILABLE");
                }
                answering = answered;
            }
        } finally {
            MDC.remove(InternalApi.CORRELATION_MDC_KEY);
        }
    }

    /** One C7 call and one R1 call. True when both services answered inside their budgets. */
    boolean round() {
        return answered(() -> customers.riskProfile(NO_CUSTOMER))
                & answered(() -> risk.evaluate(new RiskClient.Request(UUID.randomUUID(), NO_CUSTOMER,
                        WARM_UP_AMOUNT, "PHP", "APP", BigDecimal.ZERO, "TIER_3_FULL",
                        WARM_UP_DAILY_LIMIT, false)));
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
