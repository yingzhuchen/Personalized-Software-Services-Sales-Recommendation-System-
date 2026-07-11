package com.example.jobrec.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisCircuitBreakerTest {
    private RedisCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new RedisCircuitBreaker(3, 50L, 1L);
        circuitBreaker.reset();
    }

    @Test
    void startsClosedAndAllowsRequests() {
        assertEquals(RedisCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
        assertTrue(circuitBreaker.isAvailable());
    }

    @Test
    void tripsOpenAfterFailureThreshold() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertEquals(RedisCircuitBreaker.State.CLOSED, circuitBreaker.getState());

        circuitBreaker.recordFailure();
        assertEquals(RedisCircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
        assertFalse(circuitBreaker.isAvailable());
    }

    @Test
    void successResetsFailureCount() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertEquals(RedisCircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void openTransitionsToHalfOpenAfterCooldownThenClosesOnSuccess() throws InterruptedException {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertEquals(RedisCircuitBreaker.State.OPEN, circuitBreaker.getState());

        Thread.sleep(60L);
        assertTrue(circuitBreaker.allowRequest());
        assertEquals(RedisCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        circuitBreaker.recordSuccess();
        assertEquals(RedisCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.isAvailable());
    }

    @Test
    void halfOpenFailureReopensCircuit() throws InterruptedException {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        Thread.sleep(60L);
        assertTrue(circuitBreaker.allowRequest());
        assertEquals(RedisCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        circuitBreaker.recordFailure();
        assertEquals(RedisCircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }
}
