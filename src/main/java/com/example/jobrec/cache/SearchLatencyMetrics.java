package com.example.jobrec.cache;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records search-path latency for Redis cache hits vs misses so reduction
 * can be measured against a clear baseline (miss = MySQL + external APIs).
 *
 * <p>Scope: {@code RecommendationService.searchProducts} only.
 * Reduction formula: {@code 1 - p50(hit) / p50(miss)}.
 */
@Component
public class SearchLatencyMetrics {
    static final int SAMPLE_WINDOW = 4096;

    private final long[] hitSamplesNanos = new long[SAMPLE_WINDOW];
    private final long[] missSamplesNanos = new long[SAMPLE_WINDOW];
    private final AtomicInteger hitWriteIndex = new AtomicInteger();
    private final AtomicInteger missWriteIndex = new AtomicInteger();
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong hitTotalNanos = new AtomicLong();
    private final AtomicLong missTotalNanos = new AtomicLong();

    public void recordHit(long durationNanos) {
        if (durationNanos < 0L) {
            return;
        }
        int index = Math.floorMod(hitWriteIndex.getAndIncrement(), SAMPLE_WINDOW);
        hitSamplesNanos[index] = durationNanos;
        hitCount.incrementAndGet();
        hitTotalNanos.addAndGet(durationNanos);
    }

    public void recordMiss(long durationNanos) {
        if (durationNanos < 0L) {
            return;
        }
        int index = Math.floorMod(missWriteIndex.getAndIncrement(), SAMPLE_WINDOW);
        missSamplesNanos[index] = durationNanos;
        missCount.incrementAndGet();
        missTotalNanos.addAndGet(durationNanos);
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public double getHitMeanMs() {
        return meanMs(hitCount.get(), hitTotalNanos.get());
    }

    public double getMissMeanMs() {
        return meanMs(missCount.get(), missTotalNanos.get());
    }

    public double getHitP50Ms() {
        return percentileMs(copyFilledSamples(hitSamplesNanos, hitCount.get()), 0.50);
    }

    public double getMissP50Ms() {
        return percentileMs(copyFilledSamples(missSamplesNanos, missCount.get()), 0.50);
    }

    public double getHitP95Ms() {
        return percentileMs(copyFilledSamples(hitSamplesNanos, hitCount.get()), 0.95);
    }

    public double getMissP95Ms() {
        return percentileMs(copyFilledSamples(missSamplesNanos, missCount.get()), 0.95);
    }

    /**
     * @return latency reduction ratio in {@code [0, 1]}, or {@code 0} when either side lacks samples
     *         or miss p50 is zero. Example: {@code 0.80} means 80% faster on cache hit.
     */
    public double getLatencyReductionRatio() {
        double hitP50 = getHitP50Ms();
        double missP50 = getMissP50Ms();
        if (hitCount.get() == 0L || missCount.get() == 0L || missP50 <= 0.0) {
            return 0.0;
        }
        double ratio = 1.0 - (hitP50 / missP50);
        if (ratio < 0.0) {
            return 0.0;
        }
        if (ratio > 1.0) {
            return 1.0;
        }
        return ratio;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hitCount", getHitCount());
        snapshot.put("missCount", getMissCount());
        snapshot.put("hitMeanMs", round3(getHitMeanMs()));
        snapshot.put("missMeanMs", round3(getMissMeanMs()));
        snapshot.put("hitP50Ms", round3(getHitP50Ms()));
        snapshot.put("missP50Ms", round3(getMissP50Ms()));
        snapshot.put("hitP95Ms", round3(getHitP95Ms()));
        snapshot.put("missP95Ms", round3(getMissP95Ms()));
        snapshot.put("latencyReductionRatio", round3(getLatencyReductionRatio()));
        snapshot.put("latencyReductionPercent", round3(getLatencyReductionRatio() * 100.0));
        snapshot.put("sampleWindow", SAMPLE_WINDOW);
        snapshot.put("baseline", "miss_path=MySQL_catalog+SerpAPI+EdenAI; hit_path=Redis_get+JSON_parse");
        snapshot.put("scope", "RecommendationService.searchProducts");
        return snapshot;
    }

    /** Test helper. */
    public void reset() {
        Arrays.fill(hitSamplesNanos, 0L);
        Arrays.fill(missSamplesNanos, 0L);
        hitWriteIndex.set(0);
        missWriteIndex.set(0);
        hitCount.set(0L);
        missCount.set(0L);
        hitTotalNanos.set(0L);
        missTotalNanos.set(0L);
    }

    private static double meanMs(long count, long totalNanos) {
        if (count == 0L) {
            return 0.0;
        }
        return (totalNanos / (double) count) / 1_000_000.0;
    }

    private static long[] copyFilledSamples(long[] ring, long count) {
        int filled = (int) Math.min(count, SAMPLE_WINDOW);
        if (filled == 0) {
            return new long[0];
        }
        long[] copy = new long[filled];
        System.arraycopy(ring, 0, copy, 0, filled);
        return copy;
    }

    private static double percentileMs(long[] samplesNanos, double percentile) {
        if (samplesNanos.length == 0) {
            return 0.0;
        }
        long[] sorted = Arrays.copyOf(samplesNanos, samplesNanos.length);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index] / 1_000_000.0;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
