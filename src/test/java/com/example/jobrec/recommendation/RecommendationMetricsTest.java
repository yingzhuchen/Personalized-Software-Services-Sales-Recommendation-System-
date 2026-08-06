package com.example.jobrec.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationMetricsTest {
    private RecommendationMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new RecommendationMetrics();
    }

    @Test
    void snapshot_tracksOnlineQualitySignals() {
        metrics.recordRecommendation(4, 1, false);
        metrics.recordRecommendation(0, 0, true);
        metrics.recordMarketFiltered(2);
        metrics.recordClick();
        metrics.recordFavoriteFromRecommendation();

        var snapshot = metrics.snapshot();

        assertEquals(2L, snapshot.get("requests"));
        assertEquals(0.5, snapshot.get("emptyRate"));
        assertEquals(0.5, snapshot.get("coldStartRate"));
        assertEquals(0.8, snapshot.get("catalogShare"));
        assertEquals(2L, snapshot.get("marketFiltered"));
        assertEquals(1L, snapshot.get("clicks"));
        assertEquals(1L, snapshot.get("favoritesFromRecommendation"));
        assertTrue((Double) snapshot.get("clickThroughRate") > 0.0);
    }
}
