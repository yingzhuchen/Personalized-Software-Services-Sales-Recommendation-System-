package com.example.jobrec.recommendation;

import com.example.jobrec.cache.SearchLatencyMetrics;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.SerpAPIClient;
import com.example.jobrec.support.RealStoreTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-store latency benchmark against live MySQL + Redis.
 *
 * <p>Defaults are small/fast. Scale up with system properties:
 * <pre>
 *   -Dsearch.latency.samples=200
 *   -Dsearch.latency.largeSamples=1000
 *   -Dsearch.latency.extraProducts=2000
 *   -Dsearch.latency.warmup=20
 * </pre>
 *
 * Miss path: MySQL catalog LIKE + market stand-in (~80ms), then write Redis.
 * Hit path: Redis GET + JSON parse only.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.redis.host=127.0.0.1",
        "spring.redis.port=6379",
        "spring.redis.password=",
        "spring.redis.timeout=2000ms",
        "spring.redis.connect-timeout=2000ms",
        "app.redis.circuit-breaker.failure-threshold=50",
        "app.redis.circuit-breaker.open-duration-ms=1000",
        "app.redis.circuit-breaker.alert-cooldown-ms=1000"
})
@EnabledIf("com.example.jobrec.recommendation.SearchLatencyRedisMySqlIntegrationTest#storesAvailable")
class SearchLatencyRedisMySqlIntegrationTest {
    private static final int EXTRA_PRODUCTS = intProp("search.latency.extraProducts", 500);
    private static final int WARMUP = intProp("search.latency.warmup", 10);
    private static final int SAMPLES = intProp("search.latency.samples", 50);
    private static final int LARGE_SAMPLES = intProp("search.latency.largeSamples", 500);
    private static final double MIN_REDUCTION_RATIO = 0.80;
    private static final long MARKET_STAND_IN_DELAY_MS = 80L;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private SearchLatencyMetrics searchLatencyMetrics;

    @Autowired
    private StringRedisTemplate redis;

    static boolean storesAvailable() {
        RealStoreTestSupport.configureJdbcUrl();
        if (!RealStoreTestSupport.canConnect()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("redis-cli", "ping").start();
            if (process.waitFor() != 0) {
                return false;
            }
            try (java.util.Scanner scanner = new java.util.Scanner(process.getInputStream()).useDelimiter("\\A")) {
                String out = scanner.hasNext() ? scanner.next().trim() : "";
                return "PONG".equalsIgnoreCase(out);
            }
        } catch (Exception ex) {
            return false;
        }
    }

    @BeforeAll
    static void seedMysql() throws Exception {
        RealStoreTestSupport.configureJdbcUrl();
        RealStoreTestSupport.resetSchemaAndSeedCatalog(EXTRA_PRODUCTS);
        System.out.printf(Locale.US,
                "Seeded MySQL catalog: %d extra products (total ~%d)%n",
                EXTRA_PRODUCTS, EXTRA_PRODUCTS + 2);
    }

    @BeforeEach
    void clearRedisAndMetrics() {
        Set<String> keys = redis.keys("search:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        searchLatencyMetrics.reset();
    }

    @Test
    @Timeout(180)
    void realRedisHitIsAtLeast80PercentFasterThanMysqlPlusMarketMiss() throws Exception {
        Map<String, Object> report = runBenchmark("standard", SAMPLES, WARMUP);
        assertTrue(((Number) report.get("latencyReductionRatio")).doubleValue() >= MIN_REDUCTION_RATIO);
    }

    /**
     * Larger real-store load: hundreds of hit/miss iterations against live Redis/MySQL.
     * Writes {@code target/search-latency-load.json}.
     *
     * <pre>
     * mvn -Dtest=SearchLatencyRedisMySqlIntegrationTest#realStoreLargeLoadBenchmark test
     * # or scale further:
     * mvn -Dtest=SearchLatencyRedisMySqlIntegrationTest#realStoreLargeLoadBenchmark \
     *     -Dsearch.latency.largeSamples=1000 -Dsearch.latency.extraProducts=2000 test
     * </pre>
     */
    @Test
    @Timeout(900)
    void realStoreLargeLoadBenchmark() throws Exception {
        Map<String, Object> report = runBenchmark("large", LARGE_SAMPLES, Math.max(WARMUP, 20));
        assertTrue(((Number) report.get("hitCount")).longValue() >= LARGE_SAMPLES);
        assertTrue(((Number) report.get("missCount")).longValue() >= LARGE_SAMPLES);
        assertTrue(((Number) report.get("latencyReductionRatio")).doubleValue() >= MIN_REDUCTION_RATIO);
        assertTrue(((Number) report.get("hitP50Ms")).doubleValue()
                < ((Number) report.get("missP50Ms")).doubleValue());
    }

    private Map<String, Object> runBenchmark(String label, int samples, int warmup) throws Exception {
        long wallStarted = System.nanoTime();

        List<Item> missOnce = recommendationService.searchProducts(
                RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
        assertFalse(missOnce.isEmpty(), "seeded MySQL catalog should return CRM products");
        assertTrue(
                redis.hasKey(searchKey(RealStoreTestSupport.SEARCH_KEYWORD)),
                "miss path must persist search JSON into Redis");

        searchLatencyMetrics.reset();
        for (int i = 0; i < warmup; i++) {
            recommendationService.searchProducts(
                    RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
        }
        searchLatencyMetrics.reset();

        for (int i = 0; i < samples; i++) {
            List<Item> hit = recommendationService.searchProducts(
                    RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
            assertFalse(hit.isEmpty());
        }
        long hitCount = searchLatencyMetrics.getHitCount();
        double hitP50 = searchLatencyMetrics.getHitP50Ms();
        double hitP95 = searchLatencyMetrics.getHitP95Ms();
        double hitMean = searchLatencyMetrics.getHitMeanMs();

        for (int i = 0; i < samples; i++) {
            redis.delete(searchKey(RealStoreTestSupport.SEARCH_KEYWORD));
            List<Item> miss = recommendationService.searchProducts(
                    RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
            assertFalse(miss.isEmpty());
        }

        double missP50 = searchLatencyMetrics.getMissP50Ms();
        double missP95 = searchLatencyMetrics.getMissP95Ms();
        double missMean = searchLatencyMetrics.getMissMeanMs();
        double reduction = searchLatencyMetrics.getLatencyReductionRatio();
        long wallMs = (System.nanoTime() - wallStarted) / 1_000_000L;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("label", label);
        report.put("extraProducts", EXTRA_PRODUCTS);
        report.put("warmup", warmup);
        report.put("samplesPerPath", samples);
        report.put("hitCount", hitCount);
        report.put("missCount", searchLatencyMetrics.getMissCount());
        report.put("hitP50Ms", round3(hitP50));
        report.put("hitP95Ms", round3(hitP95));
        report.put("hitMeanMs", round3(hitMean));
        report.put("missP50Ms", round3(missP50));
        report.put("missP95Ms", round3(missP95));
        report.put("missMeanMs", round3(missMean));
        report.put("latencyReductionRatio", round3(reduction));
        report.put("latencyReductionPercent", round3(reduction * 100.0));
        report.put("wallClockMs", wallMs);
        report.put("marketStandInDelayMs", MARKET_STAND_IN_DELAY_MS);
        report.put("redisKey", searchKey(RealStoreTestSupport.SEARCH_KEYWORD));
        report.put("jdbc", RealStoreTestSupport.activeJdbcUrl());
        report.put("note", "MySQL+Redis are real; SerpAPI replaced by "
                + MARKET_STAND_IN_DELAY_MS + "ms stand-in (no API key)");

        Path out = writeReport(label, report);
        System.out.printf(Locale.US,
                "REAL store latency [%s]: hitP50=%.2fms hitP95=%.2fms missP50=%.2fms missP95=%.2fms "
                        + "reduction=%.1f%% hits=%d misses=%d wall=%dms report=%s%n",
                label, hitP50, hitP95, missP50, missP95, reduction * 100.0,
                hitCount, searchLatencyMetrics.getMissCount(), wallMs, out.toAbsolutePath());
        return report;
    }

    private static Path writeReport(String label, Map<String, Object> report) throws Exception {
        Path dir = Paths.get("target");
        Files.createDirectories(dir);
        Path out = dir.resolve("search-latency-" + label + ".json");
        StringBuilder json = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> entry : report.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append('"').append(value.toString().replace("\"", "\\\"")).append('"');
            } else {
                json.append(value);
            }
            if (i < report.size() - 1) {
                json.append(',');
            }
            json.append('\n');
            i++;
        }
        json.append("}\n");
        Files.write(out, json.toString().getBytes(StandardCharsets.UTF_8));
        return out;
    }

    private static String searchKey(String keyword) {
        return String.format("search:lat=%s&lon=%s&keyword=%s",
                RealStoreTestSupport.LAT, RealStoreTestSupport.LON, keyword);
    }

    private static int intProp(String key, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(raw.trim());
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * Market API stand-in: keeps miss-path cost realistic when SerpAPI keys are absent.
     * MySQL catalog + Redis remain fully real.
     */
    @TestConfiguration
    static class MarketStandInConfig {
        @Bean
        @Primary
        SerpAPIClient delayedMarketClient() {
            return new SerpAPIClient() {
                @Override
                public List<Item> search(Double lat, Double lon, String keyword) {
                    try {
                        Thread.sleep(MARKET_STAND_IN_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Item market = new Item(
                            "market-standin-" + keyword,
                            "Market Stand-in for " + keyword,
                            "Market",
                            "$1",
                            "Market",
                            Item.SOURCE_MARKET,
                            "Delayed market stand-in used only in IT",
                            null,
                            "https://market.example/" + keyword,
                            new HashSet<>(Collections.singletonList(keyword)),
                            false);
                    return Collections.singletonList(market);
                }
            };
        }
    }
}
