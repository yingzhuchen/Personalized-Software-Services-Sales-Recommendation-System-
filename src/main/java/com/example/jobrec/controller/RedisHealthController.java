package com.example.jobrec.controller;

import com.example.jobrec.cache.RedisCacheMetrics;
import com.example.jobrec.cache.RedisCircuitBreaker;
import com.example.jobrec.cache.SearchLatencyMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis availability and cache hit/miss endpoints for monitoring / alerting.
 *
 * <ul>
 *   <li>{@code GET /health/redis} — 200 when circuit is not OPEN, else 503</li>
 *   <li>{@code GET /cache/metrics} — hit/miss/error counters, circuit state, search latency</li>
 * </ul>
 */
@RestController
public class RedisHealthController {
    private final RedisCircuitBreaker circuitBreaker;
    private final RedisCacheMetrics metrics;
    private final SearchLatencyMetrics searchLatencyMetrics;

    public RedisHealthController(RedisCircuitBreaker circuitBreaker,
                                 RedisCacheMetrics metrics,
                                 SearchLatencyMetrics searchLatencyMetrics) {
        this.circuitBreaker = circuitBreaker;
        this.metrics = metrics;
        this.searchLatencyMetrics = searchLatencyMetrics;
    }

    @GetMapping("/health/redis")
    public ResponseEntity<Map<String, Object>> redisHealth() {
        Map<String, Object> body = new LinkedHashMap<>();
        RedisCircuitBreaker.State state = circuitBreaker.getState();
        boolean available = state != RedisCircuitBreaker.State.OPEN;
        body.put("status", available ? "UP" : "DOWN");
        body.put("circuitState", state.name());
        body.put("redisAvailable", available);
        body.put("consecutiveFailures", circuitBreaker.getConsecutiveFailures());
        body.put("failureThreshold", circuitBreaker.getFailureThreshold());

        if (available) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @GetMapping("/cache/metrics")
    public Map<String, Object> cacheMetrics() {
        Map<String, Object> snapshot = new LinkedHashMap<>(metrics.snapshot());
        snapshot.put("searchLatency", searchLatencyMetrics.snapshot());
        return snapshot;
    }
}
