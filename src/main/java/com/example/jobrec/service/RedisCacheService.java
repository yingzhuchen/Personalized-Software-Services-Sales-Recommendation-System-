package com.example.jobrec.service;

import com.example.jobrec.cache.RedisCacheMetrics;
import com.example.jobrec.cache.RedisCircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Cache-aside Redis access with command-level fail-open:
 * on timeout/failure or open circuit, reads return null/empty so callers
 * fall back to MySQL; writes are skipped.
 */
@Service
public class RedisCacheService {
    private static final Logger logger = LoggerFactory.getLogger(RedisCacheService.class);

    private static final String SEARCH_KEY_TEMPLATE = "search:lat=%s&lon=%s&keyword=%s";
    private static final String FAVORITE_KEY_TEMPLATE = "history:userId=%s";
    private static final String RECOMMENDATION_KEY_TEMPLATE = "recommendation:keywords:userId=%s";
    private static final String CORPUS_TOTAL_ITEMS_KEY = "corpus:total_items";
    private static final String CORPUS_KEYWORD_DF_KEY = "corpus:keyword_df";

    private final StringRedisTemplate redis;
    private final RedisCircuitBreaker circuitBreaker;
    private final RedisCacheMetrics metrics;

    public RedisCacheService(StringRedisTemplate redis,
                             RedisCircuitBreaker circuitBreaker,
                             RedisCacheMetrics metrics) {
        this.redis = redis;
        this.circuitBreaker = circuitBreaker;
        this.metrics = metrics;
    }

    public String getSearchResult(double lat, double lon, String keyword) {
        return executeRead(() ->
                redis.opsForValue().get(String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword)));
    }

    public void setSearchResult(double lat, double lon, String keyword, String value) {
        executeWrite(() ->
                redis.opsForValue().set(String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword), value));
    }

    public String getFavoriteResult(String userId) {
        return executeRead(() ->
                redis.opsForValue().get(String.format(FAVORITE_KEY_TEMPLATE, userId)));
    }

    public void setFavoriteResult(String userId, String value) {
        executeWrite(() ->
                redis.opsForValue().set(String.format(FAVORITE_KEY_TEMPLATE, userId), value));
    }

    public void deleteFavoriteResult(String userId) {
        executeWrite(() -> redis.delete(String.format(FAVORITE_KEY_TEMPLATE, userId)));
    }

    public String getRecommendationKeywords(String userId) {
        return executeRead(() ->
                redis.opsForValue().get(String.format(RECOMMENDATION_KEY_TEMPLATE, userId)));
    }

    public void setRecommendationKeywords(String userId, String keywords) {
        executeWrite(() ->
                redis.opsForValue().set(String.format(RECOMMENDATION_KEY_TEMPLATE, userId), keywords));
    }

    public void deleteRecommendationKeywords(String userId) {
        executeWrite(() -> redis.delete(String.format(RECOMMENDATION_KEY_TEMPLATE, userId)));
    }

    public String getCorpusTotalItems() {
        return executeRead(() -> redis.opsForValue().get(CORPUS_TOTAL_ITEMS_KEY));
    }

    public void setCorpusTotalItems(String totalItems) {
        executeWrite(() -> redis.opsForValue().set(CORPUS_TOTAL_ITEMS_KEY, totalItems));
    }

    public Map<String, Integer> getKeywordDocumentFrequencies(Set<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Integer> frequencies = executeRead(() -> {
            List<Object> values = redis.opsForHash().multiGet(CORPUS_KEYWORD_DF_KEY, new ArrayList<>(keywords));
            Map<String, Integer> loaded = new HashMap<>();
            int index = 0;
            for (String keyword : keywords) {
                Object value = values.get(index++);
                if (value != null) {
                    loaded.put(keyword, Integer.parseInt(value.toString()));
                }
            }
            // Treat partial/empty hash reads as a miss so callers backfill from MySQL.
            return loaded.isEmpty() ? null : loaded;
        });

        return frequencies == null ? new HashMap<>() : frequencies;
    }

    public void setKeywordDocumentFrequencies(Map<String, Integer> frequencies) {
        if (frequencies == null || frequencies.isEmpty()) {
            return;
        }
        executeWrite(() -> {
            Map<String, String> hash = new HashMap<>();
            for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
                hash.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            redis.opsForHash().putAll(CORPUS_KEYWORD_DF_KEY, hash);
        });
    }

    public void deleteCorpusCache() {
        executeWrite(() -> redis.delete(Arrays.asList(CORPUS_TOTAL_ITEMS_KEY, CORPUS_KEYWORD_DF_KEY)));
    }

    public void invalidateUserCaches(String userId) {
        deleteFavoriteResult(userId);
        deleteRecommendationKeywords(userId);
    }

    private <T> T executeRead(Supplier<T> action) {
        if (!circuitBreaker.allowRequest()) {
            metrics.recordCircuitOpenSkip();
            metrics.recordMiss();
            return null;
        }
        try {
            T result = action.get();
            circuitBreaker.recordSuccess();
            if (result == null) {
                metrics.recordMiss();
            } else {
                metrics.recordHit();
            }
            return result;
        } catch (RuntimeException ex) {
            circuitBreaker.recordFailure();
            metrics.recordError();
            metrics.recordMiss();
            logger.warn("Redis read failed; failing open to MySQL. cause={}", ex.toString());
            return null;
        }
    }

    private void executeWrite(Runnable action) {
        if (!circuitBreaker.allowRequest()) {
            metrics.recordCircuitOpenSkip();
            return;
        }
        try {
            action.run();
            circuitBreaker.recordSuccess();
        } catch (RuntimeException ex) {
            circuitBreaker.recordFailure();
            metrics.recordError();
            logger.warn("Redis write failed; skipping cache update. cause={}", ex.toString());
        }
    }
}
