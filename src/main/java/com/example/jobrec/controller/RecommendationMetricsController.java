package com.example.jobrec.controller;

import com.example.jobrec.recommendation.RecommendationEvaluationService;
import com.example.jobrec.recommendation.RecommendationMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RecommendationMetricsController {
    private final RecommendationMetrics metrics;
    private final RecommendationEvaluationService evaluationService;

    public RecommendationMetricsController(RecommendationMetrics metrics,
                                           RecommendationEvaluationService evaluationService) {
        this.metrics = metrics;
        this.evaluationService = evaluationService;
    }

    @GetMapping("/recommendation/metrics")
    public Map<String, Object> recommendationMetrics() {
        return metrics.snapshot();
    }

    @PostMapping("/recommendation/events/click")
    public Map<String, Object> recordClick(HttpSession session) {
        SessionUtils.requireSession(session);
        metrics.recordClick();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        return response;
    }

    @GetMapping("/recommendation/evaluate")
    public Map<String, Object> evaluateOffline(@RequestParam(value = "user_id", required = false) String userId,
                                               @RequestParam("lat") double lat,
                                               @RequestParam("lon") double lon,
                                               @RequestParam(value = "hold_out_ratio", defaultValue = "0.2") double holdOutRatio,
                                               @RequestParam(value = "sample_size", defaultValue = "1") int sampleSize,
                                               HttpSession session) {
        SessionUtils.requireSession(session);
        if (sampleSize <= 1 && userId != null && !userId.isEmpty()) {
            return evaluationService.evaluateUser(userId, lat, lon, holdOutRatio);
        }
        return evaluationService.evaluateSample(userId, lat, lon, holdOutRatio, Math.max(1, sampleSize));
    }
}
