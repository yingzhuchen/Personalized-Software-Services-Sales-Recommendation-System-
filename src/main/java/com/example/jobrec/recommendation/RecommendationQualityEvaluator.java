package com.example.jobrec.recommendation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline recommendation quality metrics using held-out favorites as relevance labels.
 */
public class RecommendationQualityEvaluator {

    public Map<String, Double> evaluate(List<String> recommendedItemIds, Set<String> relevantItemIds, int k) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        int limit = Math.min(k, recommendedItemIds.size());
        List<String> topK = recommendedItemIds.subList(0, limit);

        int hits = 0;
        for (String itemId : topK) {
            if (relevantItemIds.contains(itemId)) {
                hits++;
            }
        }

        metrics.put("precisionAtK", precisionAtK(hits, limit));
        metrics.put("recallAtK", recallAtK(hits, relevantItemIds.size()));
        metrics.put("f1AtK", f1(metrics.get("precisionAtK"), metrics.get("recallAtK")));
        metrics.put("ndcgAtK", ndcgAtK(topK, relevantItemIds));
        metrics.put("hitRateAtK", topK.isEmpty() ? 0.0 : (hits > 0 ? 1.0 : 0.0));
        metrics.put("coverage", recommendedItemIds.isEmpty() ? 0.0 : 1.0);
        return metrics;
    }

    public Map<String, Double> aggregate(List<Map<String, Double>> perUserMetrics) {
        Map<String, Double> aggregate = new LinkedHashMap<>();
        if (perUserMetrics.isEmpty()) {
            aggregate.put("usersEvaluated", 0.0);
            return aggregate;
        }
        aggregate.put("usersEvaluated", (double) perUserMetrics.size());
        aggregate.put("precisionAtK", average(perUserMetrics, "precisionAtK"));
        aggregate.put("recallAtK", average(perUserMetrics, "recallAtK"));
        aggregate.put("f1AtK", average(perUserMetrics, "f1AtK"));
        aggregate.put("ndcgAtK", average(perUserMetrics, "ndcgAtK"));
        aggregate.put("hitRateAtK", average(perUserMetrics, "hitRateAtK"));
        aggregate.put("coverage", average(perUserMetrics, "coverage"));
        return aggregate;
    }

    public List<String> holdOutTestItems(Set<String> favoriteItemIds, double holdOutRatio) {
        if (favoriteItemIds == null || favoriteItemIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(favoriteItemIds);
        Collections.sort(ids);
        int holdOutCount = Math.max(1, (int) Math.ceil(ids.size() * holdOutRatio));
        if (holdOutCount >= ids.size()) {
            holdOutCount = Math.max(1, ids.size() - 1);
        }
        return ids.subList(ids.size() - holdOutCount, ids.size());
    }

    public Set<String> trainingFavorites(Set<String> favoriteItemIds, List<String> heldOutItemIds) {
        Set<String> training = new HashSet<>(favoriteItemIds);
        training.removeAll(heldOutItemIds);
        return training;
    }

    private double precisionAtK(int hits, int k) {
        if (k <= 0) {
            return 0.0;
        }
        return (double) hits / (double) k;
    }

    private double recallAtK(int hits, int totalRelevant) {
        if (totalRelevant <= 0) {
            return 0.0;
        }
        return (double) hits / (double) totalRelevant;
    }

    private double f1(double precision, double recall) {
        if (precision + recall == 0.0) {
            return 0.0;
        }
        return 2.0 * precision * recall / (precision + recall);
    }

    private double ndcgAtK(List<String> recommended, Set<String> relevant) {
        if (recommended.isEmpty()) {
            return 0.0;
        }
        double dcg = 0.0;
        for (int i = 0; i < recommended.size(); i++) {
            if (relevant.contains(recommended.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }

        int idealHits = Math.min(recommended.size(), relevant.size());
        double idcg = 0.0;
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        if (idcg == 0.0) {
            return 0.0;
        }
        return dcg / idcg;
    }

    private double average(List<Map<String, Double>> metrics, String key) {
        double sum = 0.0;
        for (Map<String, Double> metric : metrics) {
            sum += metric.getOrDefault(key, 0.0);
        }
        return sum / metrics.size();
    }
}
