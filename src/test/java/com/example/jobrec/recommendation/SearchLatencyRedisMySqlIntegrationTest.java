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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-store latency benchmark:
 * <ul>
 *   <li><b>MySQL</b> — live {@code jobrec_it} catalog (seeded products)</li>
 *   <li><b>Redis</b> — live local Redis used by cache-aside search keys</li>
 *   <li><b>SerpAPI</b> — delayed stand-in (no API key in CI); ~80ms RTT like market search</li>
 * </ul>
 *
 * Miss path: MySQL catalog LIKE + market stand-in, then write Redis.
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
    private static final int EXTRA_PRODUCTS = 120;
    private static final int WARMUP = 2;
    private static final int SAMPLES = 15;
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
    @Timeout(90)
    void realRedisHitIsAtLeast80PercentFasterThanMysqlPlusMarketMiss() {
        // Cold miss populates Redis from real MySQL (+ delayed market stand-in).
        List<Item> missOnce = recommendationService.searchProducts(
                RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
        assertFalse(missOnce.isEmpty(), "seeded MySQL catalog should return CRM products");
        assertTrue(
                redis.hasKey(searchKey(RealStoreTestSupport.SEARCH_KEYWORD)),
                "miss path must persist search JSON into Redis");

        searchLatencyMetrics.reset();

        for (int i = 0; i < WARMUP; i++) {
            recommendationService.searchProducts(
                    RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
        }
        searchLatencyMetrics.reset();

        // Hit samples: real Redis reads.
        for (int i = 0; i < SAMPLES; i++) {
            List<Item> hit = recommendationService.searchProducts(
                    RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
            assertFalse(hit.isEmpty());
        }
        long hitCount = searchLatencyMetrics.getHitCount();
        double hitP50 = searchLatencyMetrics.getHitP50Ms();

        // Fresh miss samples: delete Redis key each time so path hits MySQL again.
        for (int i = 0; i < SAMPLES; i++) {
            redis.delete(searchKey(RealStoreTestSupport.SEARCH_KEYWORD));
            List<Item> miss = recommendationService.searchProducts(
                    RealStoreTestSupport.LAT, RealStoreTestSupport.LON, RealStoreTestSupport.SEARCH_KEYWORD);
            assertFalse(miss.isEmpty());
        }

        Map<String, Object> snapshot = searchLatencyMetrics.snapshot();
        double missP50 = searchLatencyMetrics.getMissP50Ms();
        double reduction = searchLatencyMetrics.getLatencyReductionRatio();

        System.out.printf(
                "REAL store latency: hitP50=%.2fms missP50=%.2fms reduction=%.1f%% hits=%d misses=%d redisKey=%s jdbc=%s%n",
                hitP50,
                missP50,
                reduction * 100.0,
                hitCount,
                searchLatencyMetrics.getMissCount(),
                searchKey(RealStoreTestSupport.SEARCH_KEYWORD),
                RealStoreTestSupport.activeJdbcUrl());

        assertTrue(hitCount >= SAMPLES);
        assertTrue(searchLatencyMetrics.getMissCount() >= SAMPLES);
        assertTrue(
                reduction >= MIN_REDUCTION_RATIO,
                String.format(
                        "Expected >=80%% reduction with real Redis/MySQL, got %.1f%% (hitP50=%.2f missP50=%.2f) snapshot=%s",
                        reduction * 100.0, hitP50, missP50, snapshot));
    }

    private static String searchKey(String keyword) {
        return String.format("search:lat=%s&lon=%s&keyword=%s",
                RealStoreTestSupport.LAT, RealStoreTestSupport.LON, keyword);
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
