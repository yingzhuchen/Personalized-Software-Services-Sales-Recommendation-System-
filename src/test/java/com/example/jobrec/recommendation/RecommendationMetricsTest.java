package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationMetricsTest {
    @Mock
    private RecommendationProperties properties;

    private RecommendationMetrics metrics;

    @BeforeEach
    void setUp() {
        when(properties.getAvgScoreAlertThreshold()).thenReturn(0.2);
        metrics = new RecommendationMetrics(properties);
    }

    @Test
    void snapshot_tracksOnlineQualitySignals() {
        metrics.recordRecommendation(4, 1, false, false, 0.35, 2);
        metrics.recordRecommendation(0, 0, true, false, 0.0, 0);
        metrics.recordRecommendation(2, 0, false, true, 0.1, 1);
        metrics.recordMarketFiltered(2);
        metrics.recordMarketSkippedForCatalogPriority();
        metrics.recordClick();
        metrics.recordFavoriteFromRecommendation();

        var snapshot = metrics.snapshot();

        assertEquals(3L, snapshot.get("requests"));
        assertEquals(1L, snapshot.get("lowConfidenceFallbacks"));
        assertEquals(6.0 / 7.0, (Double) snapshot.get("catalogShare"), 1e-9);
        assertEquals(3L, snapshot.get("lowScoreFiltered"));
        assertEquals(1L, snapshot.get("marketSkippedForCatalogPriority"));
        assertEquals(1L, snapshot.get("clicks"));
        assertTrue((Double) snapshot.get("avgScore") > 0.0);
    }

    @Test
    void recordRecommendation_emitsAlertWhenAverageScoreIsLow() {
        metrics.recordRecommendation(1, 0, false, false, 0.05, 0);

        assertEquals(1L, metrics.snapshot().get("avgScoreAlerts"));
    }
}
