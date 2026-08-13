package com.example.jobrec.controller;

import com.example.jobrec.recommendation.RecommendationEvaluationService;
import com.example.jobrec.recommendation.RecommendationMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationMetricsController.class)
class RecommendationMetricsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationMetrics metrics;

    @MockBean
    private RecommendationEvaluationService evaluationService;

    @Test
    void recommendationMetrics_returnsSnapshot() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requests", 10L);
        snapshot.put("precisionAtK", 0.4);
        when(metrics.snapshot()).thenReturn(snapshot);

        mockMvc.perform(get("/recommendation/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests").value(10));
    }

    @Test
    void evaluateOffline_returnsEvaluationPayload() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "OK");
        payload.put("metrics", Collections.singletonMap("precisionAtK", 0.5));
        when(evaluationService.evaluateUser("user-1", 1.0, 2.0, 0.2)).thenReturn(payload);

        mockMvc.perform(get("/recommendation/evaluate")
                        .param("user_id", "user-1")
                        .param("lat", "1.0")
                        .param("lon", "2.0"))
                .andExpect(status().isForbidden());
    }
}
