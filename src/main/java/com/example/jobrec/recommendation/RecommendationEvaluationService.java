package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationEvaluationService {
    private final RecommendationService recommendationService;
    private final RecommendationProperties properties;
    private final RecommendationQualityEvaluator evaluator = new RecommendationQualityEvaluator();

    public RecommendationEvaluationService(RecommendationService recommendationService,
                                           RecommendationProperties properties) {
        this.recommendationService = recommendationService;
        this.properties = properties;
    }

    public Map<String, Object> evaluateUser(String userId, double lat, double lon, double holdOutRatio) {
        MySQLConnection connection = new MySQLConnection();
        Set<String> favoriteItemIds = connection.getFavoriteItemIds(userId);
        connection.close();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("k", properties.getMaxResults());

        if (favoriteItemIds.size() < 2) {
            response.put("status", "INSUFFICIENT_DATA");
            response.put("message", "Need at least 2 favorites for hold-out evaluation");
            return response;
        }

        List<String> heldOutItems = evaluator.holdOutTestItems(favoriteItemIds, holdOutRatio);
        Set<String> trainingFavorites = evaluator.trainingFavorites(favoriteItemIds, heldOutItems);
        Set<String> excluded = new HashSet<>(trainingFavorites);

        List<Item> recommended = recommendationService.recommendItemsForEvaluation(
                trainingFavorites, excluded, lat, lon);
        List<String> recommendedIds = recommended.stream()
                .map(item -> item.getId())
                .collect(Collectors.toList());

        Map<String, Double> metrics = evaluator.evaluate(
                recommendedIds, new HashSet<>(heldOutItems), properties.getMaxResults());
        response.put("status", "OK");
        response.put("heldOutCount", heldOutItems.size());
        response.put("trainingFavoriteCount", trainingFavorites.size());
        response.put("recommendedCount", recommendedIds.size());
        response.put("metrics", metrics);
        return response;
    }

    public Map<String, Object> evaluateSample(String userId,
                                              double lat,
                                              double lon,
                                              double holdOutRatio,
                                              int sampleSize) {
        MySQLConnection connection = new MySQLConnection();
        List<String> userIds = connection.getUsersWithMinimumFavorites(2, sampleSize);
        connection.close();

        if (userId != null && !userId.isEmpty() && !userIds.contains(userId)) {
            userIds.add(0, userId);
        }

        List<Map<String, Double>> perUserMetrics = new ArrayList<>();
        for (String candidateUserId : userIds) {
            Map<String, Object> result = evaluateUser(candidateUserId, lat, lon, holdOutRatio);
            if ("OK".equals(result.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = (Map<String, Double>) result.get("metrics");
                perUserMetrics.add(metrics);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("k", properties.getMaxResults());
        response.put("sampleSizeRequested", sampleSize);
        response.put("usersEvaluated", perUserMetrics.size());
        response.put("aggregateMetrics", evaluator.aggregate(perUserMetrics));
        return response;
    }
}
