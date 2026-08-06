package com.example.jobrec.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecommendationProperties {
    private final int topKeywordCount;
    private final int marketSupplementPerKeyword;
    private final int maxResults;
    private final int marketMinKeywordOverlap;
    private final int coldStartFallbackSize;
    private final boolean coldStartFallbackEnabled;
    private final double minItemScore;
    private final int minResultsBeforeFallback;
    private final boolean lowConfidenceFallbackEnabled;
    private final double avgScoreAlertThreshold;
    private final int maxMarketResults;
    private final int skipMarketWhenCatalogAtLeast;

    public RecommendationProperties(
            @Value("${app.recommendation.top-keyword-count:3}") int topKeywordCount,
            @Value("${app.recommendation.market-supplement-per-keyword:3}") int marketSupplementPerKeyword,
            @Value("${app.recommendation.max-results:50}") int maxResults,
            @Value("${app.recommendation.market-min-keyword-overlap:1}") int marketMinKeywordOverlap,
            @Value("${app.recommendation.cold-start-fallback-size:5}") int coldStartFallbackSize,
            @Value("${app.recommendation.cold-start-fallback-enabled:true}") boolean coldStartFallbackEnabled,
            @Value("${app.recommendation.min-item-score:0.15}") double minItemScore,
            @Value("${app.recommendation.min-results-before-fallback:3}") int minResultsBeforeFallback,
            @Value("${app.recommendation.low-confidence-fallback-enabled:true}") boolean lowConfidenceFallbackEnabled,
            @Value("${app.recommendation.avg-score-alert-threshold:0.2}") double avgScoreAlertThreshold,
            @Value("${app.recommendation.max-market-results:5}") int maxMarketResults,
            @Value("${app.recommendation.skip-market-when-catalog-at-least:3}") int skipMarketWhenCatalogAtLeast) {
        this.topKeywordCount = Math.max(1, topKeywordCount);
        this.marketSupplementPerKeyword = Math.max(0, marketSupplementPerKeyword);
        this.maxResults = Math.max(1, maxResults);
        this.marketMinKeywordOverlap = Math.max(1, marketMinKeywordOverlap);
        this.coldStartFallbackSize = Math.max(1, coldStartFallbackSize);
        this.coldStartFallbackEnabled = coldStartFallbackEnabled;
        this.minItemScore = Math.max(0.0, minItemScore);
        this.minResultsBeforeFallback = Math.max(1, minResultsBeforeFallback);
        this.lowConfidenceFallbackEnabled = lowConfidenceFallbackEnabled;
        this.avgScoreAlertThreshold = Math.max(0.0, avgScoreAlertThreshold);
        this.maxMarketResults = Math.max(0, maxMarketResults);
        this.skipMarketWhenCatalogAtLeast = Math.max(0, skipMarketWhenCatalogAtLeast);
    }

    public int getTopKeywordCount() {
        return topKeywordCount;
    }

    public int getMarketSupplementPerKeyword() {
        return marketSupplementPerKeyword;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public int getMarketMinKeywordOverlap() {
        return marketMinKeywordOverlap;
    }

    public int getColdStartFallbackSize() {
        return coldStartFallbackSize;
    }

    public boolean isColdStartFallbackEnabled() {
        return coldStartFallbackEnabled;
    }

    public double getMinItemScore() {
        return minItemScore;
    }

    public int getMinResultsBeforeFallback() {
        return minResultsBeforeFallback;
    }

    public boolean isLowConfidenceFallbackEnabled() {
        return lowConfidenceFallbackEnabled;
    }

    public double getAvgScoreAlertThreshold() {
        return avgScoreAlertThreshold;
    }

    public int getMaxMarketResults() {
        return maxMarketResults;
    }

    public int getSkipMarketWhenCatalogAtLeast() {
        return skipMarketWhenCatalogAtLeast;
    }
}
