package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process online recommendation metrics for monitoring quality in production.
 */
@Component
public class RecommendationMetrics {
    private final RecommendationProperties properties;
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong emptyResponses = new AtomicLong();
    private final AtomicLong coldStartFallbacks = new AtomicLong();
    private final AtomicLong lowConfidenceFallbacks = new AtomicLong();
    private final AtomicLong catalogResults = new AtomicLong();
    private final AtomicLong marketResults = new AtomicLong();
    private final AtomicLong marketFiltered = new AtomicLong();
    private final AtomicLong lowScoreFiltered = new AtomicLong();
    private final AtomicLong marketSkippedForCatalogPriority = new AtomicLong();
    private final AtomicLong clicks = new AtomicLong();
    private final AtomicLong favoritesFromRecommendation = new AtomicLong();
    private final AtomicLong scoreTotal = new AtomicLong();
    private final AtomicLong scoreSamples = new AtomicLong();
    private final AtomicLong avgScoreAlerts = new AtomicLong();

    public RecommendationMetrics(RecommendationProperties properties) {
        this.properties = properties;
    }

    public void recordRecommendation(int catalogCount,
                                     int marketCount,
                                     boolean coldStartFallback,
                                     boolean lowConfidenceFallback,
                                     double avgScore,
                                     int lowScoreFilteredCount) {
        requests.incrementAndGet();
        catalogResults.addAndGet(catalogCount);
        marketResults.addAndGet(marketCount);
        if (catalogCount + marketCount == 0) {
            emptyResponses.incrementAndGet();
        }
        if (coldStartFallback) {
            coldStartFallbacks.incrementAndGet();
        }
        if (lowConfidenceFallback) {
            lowConfidenceFallbacks.incrementAndGet();
        }
        if (lowScoreFilteredCount > 0) {
            lowScoreFiltered.addAndGet(lowScoreFilteredCount);
        }
        if (avgScore > 0.0) {
            scoreTotal.addAndGet((long) (avgScore * 1000.0));
            scoreSamples.incrementAndGet();
            maybeAlertLowAverageScore(avgScore);
        }
    }

    public void recordMarketFiltered(int filteredCount) {
        marketFiltered.addAndGet(filteredCount);
    }

    public void recordMarketSkippedForCatalogPriority() {
        marketSkippedForCatalogPriority.incrementAndGet();
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
        snapshot.put("lowConfidenceFallbacks", lowConfidenceFallbacks.get());
        snapshot.put("lowConfidenceFallbackRate", rate(lowConfidenceFallbacks.get(), totalRequests));
        snapshot.put("catalogResults", catalogResults.get());
        snapshot.put("marketResults", marketResults.get());
        snapshot.put("marketFiltered", marketFiltered.get());
        snapshot.put("lowScoreFiltered", lowScoreFiltered.get());
        snapshot.put("marketSkippedForCatalogPriority", marketSkippedForCatalogPriority.get());
        snapshot.put("avgCatalogPerRequest", average(catalogResults.get(), totalRequests));
        snapshot.put("avgMarketPerRequest", average(marketResults.get(), totalRequests));
        snapshot.put("catalogShare", rate(catalogResults.get(), totalResults));
        snapshot.put("avgScore", averageScore());
        snapshot.put("avgScoreAlertThreshold", properties.getAvgScoreAlertThreshold());
        snapshot.put("avgScoreAlerts", avgScoreAlerts.get());
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
        lowConfidenceFallbacks.set(0);
        catalogResults.set(0);
        marketResults.set(0);
        marketFiltered.set(0);
        lowScoreFiltered.set(0);
        marketSkippedForCatalogPriority.set(0);
        clicks.set(0);
        favoritesFromRecommendation.set(0);
        scoreTotal.set(0);
        scoreSamples.set(0);
        avgScoreAlerts.set(0);
    }

    private void maybeAlertLowAverageScore(double avgScore) {
        if (avgScore >= properties.getAvgScoreAlertThreshold()) {
            return;
        }
        avgScoreAlerts.incrementAndGet();
        System.err.println("ALERT recommendation_avg_score=LOW avg_score="
                + String.format("%.4f", avgScore)
                + " threshold=" + properties.getAvgScoreAlertThreshold()
                + " action=\"review keyword profile, catalog coverage, or min-item-score threshold\"");
    }

    private double averageScore() {
        long samples = scoreSamples.get();
        if (samples == 0L) {
            return 0.0;
        }
        return (scoreTotal.get() / 1000.0) / (double) samples;
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
