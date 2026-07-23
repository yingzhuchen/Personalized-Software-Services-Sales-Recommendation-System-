package com.example.jobrec.controller;

import com.example.jobrec.cache.RedisCacheMetrics;
import com.example.jobrec.cache.RedisCircuitBreaker;
import com.example.jobrec.cache.SearchLatencyMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedisHealthController.class)
class RedisHealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedisCircuitBreaker circuitBreaker;

    @MockBean
    private RedisCacheMetrics metrics;

    @MockBean
    private SearchLatencyMetrics searchLatencyMetrics;

    @Test
    void redisHealth_returnsOkWhenCircuitClosed() throws Exception {
        when(circuitBreaker.getState()).thenReturn(RedisCircuitBreaker.State.CLOSED);
        when(circuitBreaker.getConsecutiveFailures()).thenReturn(0);
        when(circuitBreaker.getFailureThreshold()).thenReturn(5);

        mockMvc.perform(get("/health/redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.circuitState").value("CLOSED"))
                .andExpect(jsonPath("$.redisAvailable").value(true));
    }

    @Test
    void redisHealth_returnsServiceUnavailableWhenCircuitOpen() throws Exception {
        when(circuitBreaker.getState()).thenReturn(RedisCircuitBreaker.State.OPEN);
        when(circuitBreaker.getConsecutiveFailures()).thenReturn(5);
        when(circuitBreaker.getFailureThreshold()).thenReturn(5);

        mockMvc.perform(get("/health/redis"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.circuitState").value("OPEN"))
                .andExpect(jsonPath("$.redisAvailable").value(false));
    }

    @Test
    void cacheMetrics_returnsSnapshot() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hits", 10L);
        snapshot.put("misses", 2L);
        snapshot.put("errors", 1L);
        snapshot.put("circuitOpenSkips", 0L);
        snapshot.put("hitRate", 10.0 / 12.0);
        snapshot.put("circuitState", "CLOSED");
        snapshot.put("redisAvailable", true);
        when(metrics.snapshot()).thenReturn(snapshot);

        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("hitCount", 8L);
        latency.put("missCount", 2L);
        latency.put("latencyReductionPercent", 85.0);
        latency.put("scope", "RecommendationService.searchProducts");
        when(searchLatencyMetrics.snapshot()).thenReturn(latency);

        mockMvc.perform(get("/cache/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits").value(10))
                .andExpect(jsonPath("$.misses").value(2))
                .andExpect(jsonPath("$.hitRate").value(10.0 / 12.0))
                .andExpect(jsonPath("$.circuitState").value("CLOSED"))
                .andExpect(jsonPath("$.searchLatency.latencyReductionPercent").value(85.0))
                .andExpect(jsonPath("$.searchLatency.scope")
                        .value("RecommendationService.searchProducts"));
    }
}
