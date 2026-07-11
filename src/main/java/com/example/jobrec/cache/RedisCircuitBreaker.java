package com.example.jobrec.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple three-state circuit breaker for Redis:
 * CLOSED → OPEN after consecutive failures; OPEN → HALF_OPEN after cooldown;
 * HALF_OPEN → CLOSED on success or OPEN on failure.
 */
@Component
public class RedisCircuitBreaker {
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static final Logger logger = LoggerFactory.getLogger(RedisCircuitBreaker.class);

    private final int failureThreshold;
    private final long openDurationMs;
    private final long alertCooldownMs;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAtMs = new AtomicLong(0);
    private final AtomicLong lastAlertAtMs = new AtomicLong(0);

    public RedisCircuitBreaker(
            @Value("${app.redis.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${app.redis.circuit-breaker.open-duration-ms:30000}") long openDurationMs,
            @Value("${app.redis.circuit-breaker.alert-cooldown-ms:60000}") long alertCooldownMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1L, openDurationMs);
        this.alertCooldownMs = Math.max(1L, alertCooldownMs);
    }

    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED || current == State.HALF_OPEN) {
            return true;
        }
        if (System.currentTimeMillis() - openedAtMs.get() >= openDurationMs
                && state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            logger.info("Redis circuit breaker transitioned OPEN -> HALF_OPEN");
            return true;
        }
        return state.get() == State.HALF_OPEN;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        State previous = state.getAndSet(State.CLOSED);
        if (previous != State.CLOSED) {
            logger.info("Redis circuit breaker transitioned {} -> CLOSED after success", previous);
        }
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state.get() == State.HALF_OPEN) {
            tripOpen("probe failure while HALF_OPEN");
            return;
        }
        if (failures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            openedAtMs.set(System.currentTimeMillis());
            emitAvailabilityAlert(failures + " consecutive Redis failures (threshold=" + failureThreshold + ")");
        }
    }

    public State getState() {
        if (state.get() == State.OPEN
                && System.currentTimeMillis() - openedAtMs.get() >= openDurationMs) {
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
        }
        return state.get();
    }

    public boolean isAvailable() {
        return getState() != State.OPEN;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public long getOpenDurationMs() {
        return openDurationMs;
    }

    /** Test helper. */
    public void reset() {
        consecutiveFailures.set(0);
        openedAtMs.set(0);
        lastAlertAtMs.set(0);
        state.set(State.CLOSED);
    }

    private void tripOpen(String reason) {
        openedAtMs.set(System.currentTimeMillis());
        consecutiveFailures.set(failureThreshold);
        state.set(State.OPEN);
        emitAvailabilityAlert(reason);
    }

    private void emitAvailabilityAlert(String reason) {
        long now = System.currentTimeMillis();
        long last = lastAlertAtMs.get();
        if (now - last < alertCooldownMs) {
            return;
        }
        if (!lastAlertAtMs.compareAndSet(last, now)) {
            return;
        }
        // Structured alert line for log-based monitoring (CloudWatch Logs metric filters, etc.)
        logger.error(
                "ALERT redis_availability=DOWN circuit_state=OPEN reason=\"{}\" "
                        + "action=\"fail-open to MySQL; investigate Redis connectivity/latency\"",
                reason);
    }
}
