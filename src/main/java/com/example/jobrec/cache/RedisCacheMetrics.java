package com.example.jobrec.cache;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process Redis cache hit/miss/error counters for monitoring and alerting.
 */
@Component
public class RedisCacheMetrics {
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong circuitOpenSkips = new AtomicLong();
    private final RedisCircuitBreaker circuitBreaker;

    public RedisCacheMetrics(RedisCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public void recordCircuitOpenSkip() {
        circuitOpenSkips.incrementAndGet();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public long getErrors() {
        return errors.get();
    }

    public long getCircuitOpenSkips() {
        return circuitOpenSkips.get();
    }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        if (total == 0L) {
            return 0.0;
        }
        return (double) hits.get() / (double) total;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hits", getHits());
        snapshot.put("misses", getMisses());
        snapshot.put("errors", getErrors());
        snapshot.put("circuitOpenSkips", getCircuitOpenSkips());
        snapshot.put("hitRate", getHitRate());
        snapshot.put("circuitState", circuitBreaker.getState().name());
        snapshot.put("redisAvailable", circuitBreaker.isAvailable());
        snapshot.put("consecutiveFailures", circuitBreaker.getConsecutiveFailures());
        snapshot.put("failureThreshold", circuitBreaker.getFailureThreshold());
        snapshot.put("openDurationMs", circuitBreaker.getOpenDurationMs());
        return snapshot;
    }

    /** Test helper. */
    public void reset() {
        hits.set(0);
        misses.set(0);
        errors.set(0);
        circuitOpenSkips.set(0);
    }
}
