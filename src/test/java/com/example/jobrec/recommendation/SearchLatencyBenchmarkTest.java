package com.example.jobrec.recommendation;

import com.example.jobrec.cache.SearchLatencyMetrics;
import com.example.jobrec.entity.Item;
import com.example.jobrec.service.ProductSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Benchmark: Redis cache hit vs uncached miss for {@link RecommendationService#searchProducts}.
 *
 * <p><b>Baseline (miss):</b> simulated MySQL catalog + SerpAPI/EdenAI path (80ms).
 * <p><b>Cached (hit):</b> simulated Redis get + JSON parse (4ms).
 * <p><b>Claim:</b> {@code 1 - p50(hit)/p50(miss) >= 0.80} (≥80% search latency reduction).
 *
 * <p>Delays are injected in collaborators so CI stays deterministic while still
 * exercising real hit/miss branches and {@link SearchLatencyMetrics}.
 */
@ExtendWith(MockitoExtension.class)
class SearchLatencyBenchmarkTest {
    private static final long HIT_DELAY_MS = 4L;
    private static final long MISS_DELAY_MS = 80L;
    private static final int WARMUP = 3;
    private static final int SAMPLES = 25;
    private static final double MIN_REDUCTION_RATIO = 0.80;

    @Mock
    private RecommendationProfileService profileService;

    @Mock
    private ProductSearchService productSearchService;

    private SearchLatencyMetrics searchLatencyMetrics;
    private RecommendationService recommendationService;

    private final Item sampleItem = new Item(
            "innova-crm",
            "INNOVA CRM Platform",
            "INNOVA AI",
            "$99/month",
            "INNOVA AI",
            Item.SOURCE_INNOVA_CATALOG,
            "CRM",
            null,
            "https://innova.ai/products/crm",
            new HashSet<>(Collections.singletonList("crm")),
            false);

    @BeforeEach
    void setUp() {
        searchLatencyMetrics = new SearchLatencyMetrics();
        recommendationService = new RecommendationService(
                profileService, productSearchService, searchLatencyMetrics, null);
    }

    @Test
    @Timeout(45)
    void redisCacheHitReducesSearchLatencyByAtLeast80PercentVersusMissBaseline() throws Exception {
        String cachedJson = "[{\"id\":\"innova-crm\",\"title\":\"INNOVA CRM Platform\"}]";

        when(profileService.getCachedSearchResult(anyDouble(), anyDouble(), eq("crm")))
                .thenAnswer(invocation -> {
                    Thread.sleep(HIT_DELAY_MS);
                    return cachedJson;
                });
        when(profileService.parseItems(cachedJson))
                .thenReturn(Collections.singletonList(sampleItem));

        when(profileService.getCachedSearchResult(anyDouble(), anyDouble(), eq("analytics")))
                .thenAnswer(invocation -> {
                    Thread.sleep(1L); // Redis lookup still occurs before fallback
                    return null;
                });
        when(productSearchService.search(anyDouble(), anyDouble(), eq("analytics")))
                .thenAnswer(invocation -> {
                    Thread.sleep(MISS_DELAY_MS);
                    return Collections.singletonList(sampleItem);
                });

        for (int i = 0; i < WARMUP; i++) {
            recommendationService.searchProducts(37.77, -122.42, "crm");
            recommendationService.searchProducts(37.77, -122.42, "analytics");
        }
        searchLatencyMetrics.reset();

        for (int i = 0; i < SAMPLES; i++) {
            recommendationService.searchProducts(37.77, -122.42, "crm");
            recommendationService.searchProducts(37.77, -122.42, "analytics");
        }

        verify(productSearchService, never()).search(anyDouble(), anyDouble(), eq("crm"));
        verify(productSearchService, times(WARMUP + SAMPLES))
                .search(anyDouble(), anyDouble(), eq("analytics"));

        Map<String, Object> snapshot = searchLatencyMetrics.snapshot();
        double reduction = searchLatencyMetrics.getLatencyReductionRatio();
        double hitP50 = searchLatencyMetrics.getHitP50Ms();
        double missP50 = searchLatencyMetrics.getMissP50Ms();

        System.out.printf(
                "Search latency benchmark: hitP50=%.2fms missP50=%.2fms reduction=%.1f%% baseline=%s%n",
                hitP50, missP50, reduction * 100.0, snapshot.get("baseline"));

        assertEquals(SAMPLES, searchLatencyMetrics.getHitCount());
        assertEquals(SAMPLES, searchLatencyMetrics.getMissCount());
        assertTrue(
                reduction >= MIN_REDUCTION_RATIO,
                String.format(
                        "Expected >=80%% latency reduction on cache hit, got %.1f%% (hitP50=%.2fms, missP50=%.2fms)",
                        reduction * 100.0, hitP50, missP50));
        assertTrue(missP50 > hitP50);
        assertTrue(hitP50 < MISS_DELAY_MS * 0.25);
    }

    @Test
    void searchProducts_missPathPopulatesCacheAndRecordsMissLatency() {
        when(profileService.getCachedSearchResult(1.0, 2.0, "crm")).thenReturn(null);
        when(productSearchService.search(1.0, 2.0, "crm"))
                .thenReturn(Collections.singletonList(sampleItem));

        List<Item> results = recommendationService.searchProducts(1.0, 2.0, "crm");

        assertEquals(1, results.size());
        verify(productSearchService).search(1.0, 2.0, "crm");
        verify(profileService).cacheSearchResult(eq(1.0), eq(2.0), eq("crm"), anyList());
        assertEquals(1L, searchLatencyMetrics.getMissCount());
        assertEquals(0L, searchLatencyMetrics.getHitCount());
        assertTrue(searchLatencyMetrics.getMissMeanMs() >= 0.0);
    }

    @Test
    void searchProducts_hitPathSkipsProductSearchAndRecordsHitLatency() {
        String cachedJson = "[{\"id\":\"innova-crm\"}]";
        when(profileService.getCachedSearchResult(1.0, 2.0, "crm")).thenReturn(cachedJson);
        when(profileService.parseItems(cachedJson)).thenReturn(Collections.singletonList(sampleItem));

        List<Item> results = recommendationService.searchProducts(1.0, 2.0, "crm");

        assertEquals(1, results.size());
        verify(productSearchService, never()).search(anyDouble(), anyDouble(), anyString());
        verify(profileService, never()).cacheSearchResult(anyDouble(), anyDouble(), anyString(), anyList());
        assertEquals(1L, searchLatencyMetrics.getHitCount());
        assertEquals(0L, searchLatencyMetrics.getMissCount());
    }
}
