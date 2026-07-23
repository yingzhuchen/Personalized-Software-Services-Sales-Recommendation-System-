package com.example.jobrec.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchLatencyMetricsTest {
    private SearchLatencyMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new SearchLatencyMetrics();
        metrics.reset();
    }

    @Test
    void reductionIsZeroUntilBothHitAndMissExist() {
        metrics.recordHit(TimeUnit.MILLISECONDS.toNanos(5));
        assertEquals(0.0, metrics.getLatencyReductionRatio());
    }

    @Test
    void reductionReflectsHitVersusMissP50() {
        // hits ~5ms, misses ~50ms => ~90% reduction
        for (int i = 0; i < 10; i++) {
            metrics.recordHit(TimeUnit.MILLISECONDS.toNanos(5));
            metrics.recordMiss(TimeUnit.MILLISECONDS.toNanos(50));
        }

        assertEquals(10L, metrics.getHitCount());
        assertEquals(10L, metrics.getMissCount());
        assertEquals(5.0, metrics.getHitP50Ms(), 0.1);
        assertEquals(50.0, metrics.getMissP50Ms(), 0.1);
        assertEquals(0.90, metrics.getLatencyReductionRatio(), 0.02);
    }

    @Test
    void snapshotIncludesBaselineAndScope() {
        metrics.recordHit(TimeUnit.MILLISECONDS.toNanos(4));
        metrics.recordMiss(TimeUnit.MILLISECONDS.toNanos(40));

        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(1L, snapshot.get("hitCount"));
        assertEquals(1L, snapshot.get("missCount"));
        assertTrue(((Number) snapshot.get("latencyReductionPercent")).doubleValue() >= 80.0);
        assertEquals("RecommendationService.searchProducts", snapshot.get("scope"));
        assertTrue(snapshot.get("baseline").toString().contains("Redis_get"));
    }

    @Test
    void ignoresNegativeDurations() {
        metrics.recordHit(-1L);
        metrics.recordMiss(-5L);
        assertEquals(0L, metrics.getHitCount());
        assertEquals(0L, metrics.getMissCount());
    }
}
