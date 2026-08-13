package com.example.jobrec.recommendation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationQualityEvaluatorTest {
    private final RecommendationQualityEvaluator evaluator = new RecommendationQualityEvaluator();

    @Test
    void evaluate_computesPrecisionRecallAndNdcg() {
        List<String> recommended = Arrays.asList("a", "b", "c", "d");
        Set<String> relevant = new LinkedHashSet<>(Arrays.asList("b", "d", "z"));

        Map<String, Double> metrics = evaluator.evaluate(recommended, relevant, 4);

        assertEquals(0.5, metrics.get("precisionAtK"), 1e-9);
        assertEquals(2.0 / 3.0, metrics.get("recallAtK"), 1e-9);
        assertTrue(metrics.get("ndcgAtK") > 0.0);
        assertEquals(1.0, metrics.get("hitRateAtK"), 1e-9);
    }

    @Test
    void holdOutTestItems_reservesMostRecentSortedIds() {
        Set<String> favorites = new LinkedHashSet<>(Arrays.asList("c", "a", "b", "d"));

        List<String> heldOut = evaluator.holdOutTestItems(favorites, 0.25);

        assertEquals(1, heldOut.size());
        assertEquals("d", heldOut.get(0));
    }

    @Test
    void aggregate_averagesPerUserMetrics() {
        Map<String, Double> first = evaluator.evaluate(
                Arrays.asList("a", "b"), new LinkedHashSet<>(Arrays.asList("a")), 2);
        Map<String, Double> second = evaluator.evaluate(
                Arrays.asList("x", "y"), new LinkedHashSet<>(Arrays.asList("y")), 2);

        Map<String, Double> aggregate = evaluator.aggregate(Arrays.asList(first, second));

        assertEquals(2.0, aggregate.get("usersEvaluated"), 1e-9);
        assertEquals(0.5, aggregate.get("precisionAtK"), 1e-9);
        assertEquals(1.0, aggregate.get("recallAtK"), 1e-9);
    }
}
