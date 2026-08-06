package com.example.jobrec.recommendation;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process online recommendation metrics for monitoring quality in production.
 */
@Component
public class RecommendationMetrics {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong emptyResponses = new AtomicLong();
    private final AtomicLong coldStartFallbacks = new AtomicLong();
    private final AtomicLong catalogResults = new AtomicLong();
    private final AtomicLong marketResults = new AtomicLong();
    private final AtomicLong marketFiltered = new AtomicLong();
    private final AtomicLong clicks = new AtomicLong();
    private final AtomicLong favoritesFromRecommendation = new AtomicLong();

    public void recordRecommendation(int catalogCount, int marketCount, boolean coldStartFallback) {
        requests.incrementAndGet();
        catalogResults.addAndGet(catalogCount);
        marketResults.addAndGet(marketCount);
        if (catalogCount + marketCount == 0) {
            emptyResponses.incrementAndGet();
        }
        if (coldStartFallback) {
            coldStartFallbacks.incrementAndGet();
        }
    }

    public void recordMarketFiltered(int filteredCount) {
        marketFiltered.addAndGet(filteredCount);
    }

    public void recordClick() {
        clicks.incrementAndGet();
    }

    public void recordFavoriteFromRecommendation() {
        favoritesFromRecommendation.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        long totalRequests = requests.get();
        long totalResults = catalogResults.get() + marketResults.get();
        snapshot.put("requests", totalRequests);
        snapshot.put("emptyResponses", emptyResponses.get());
        snapshot.put("emptyRate", rate(emptyResponses.get(), totalRequests));
        snapshot.put("coldStartFallbacks", coldStartFallbacks.get());
        snapshot.put("coldStartRate", rate(coldStartFallbacks.get(), totalRequests));
        snapshot.put("catalogResults", catalogResults.get());
        snapshot.put("marketResults", marketResults.get());
        snapshot.put("marketFiltered", marketFiltered.get());
        snapshot.put("avgCatalogPerRequest", average(catalogResults.get(), totalRequests));
        snapshot.put("avgMarketPerRequest", average(marketResults.get(), totalRequests));
        snapshot.put("catalogShare", rate(catalogResults.get(), totalResults));
        snapshot.put("clicks", clicks.get());
        snapshot.put("favoritesFromRecommendation", favoritesFromRecommendation.get());
        snapshot.put("clickThroughRate", rate(clicks.get(), totalResults));
        snapshot.put("favoriteRate", rate(favoritesFromRecommendation.get(), totalResults));
        return snapshot;
    }

    public void reset() {
        requests.set(0);
        emptyResponses.set(0);
        coldStartFallbacks.set(0);
        catalogResults.set(0);
        marketResults.set(0);
        marketFiltered.set(0);
        clicks.set(0);
        favoritesFromRecommendation.set(0);
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
    }

    private double average(long sum, long count) {
        if (count == 0L) {
            return 0.0;
        }
        return (double) sum / (double) count;
    }
}
