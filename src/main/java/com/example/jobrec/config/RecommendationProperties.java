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

    public RecommendationProperties(
            @Value("${app.recommendation.top-keyword-count:3}") int topKeywordCount,
            @Value("${app.recommendation.market-supplement-per-keyword:3}") int marketSupplementPerKeyword,
            @Value("${app.recommendation.max-results:50}") int maxResults,
            @Value("${app.recommendation.market-min-keyword-overlap:1}") int marketMinKeywordOverlap,
            @Value("${app.recommendation.cold-start-fallback-size:5}") int coldStartFallbackSize,
            @Value("${app.recommendation.cold-start-fallback-enabled:true}") boolean coldStartFallbackEnabled) {
        this.topKeywordCount = Math.max(1, topKeywordCount);
        this.marketSupplementPerKeyword = Math.max(0, marketSupplementPerKeyword);
        this.maxResults = Math.max(1, maxResults);
        this.marketMinKeywordOverlap = Math.max(1, marketMinKeywordOverlap);
        this.coldStartFallbackSize = Math.max(1, coldStartFallbackSize);
        this.coldStartFallbackEnabled = coldStartFallbackEnabled;
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
}
