package com.example.jobrec.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RedisCacheService {
    private static final String SEARCH_KEY_TEMPLATE = "search:lat=%s&lon=%s&keyword=%s";
    private static final String FAVORITE_KEY_TEMPLATE = "history:userId=%s";
    private static final String RECOMMENDATION_KEY_TEMPLATE = "recommendation:keywords:userId=%s";
    private static final String CORPUS_TOTAL_ITEMS_KEY = "corpus:total_items";
    private static final String CORPUS_KEYWORD_DF_KEY = "corpus:keyword_df";

    private final StringRedisTemplate redis;

    public RedisCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String getSearchResult(double lat, double lon, String keyword) {
        return redis.opsForValue().get(String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword));
    }

    public void setSearchResult(double lat, double lon, String keyword, String value) {
        redis.opsForValue().set(String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword), value);
    }

    public String getFavoriteResult(String userId) {
        return redis.opsForValue().get(String.format(FAVORITE_KEY_TEMPLATE, userId));
    }

    public void setFavoriteResult(String userId, String value) {
        redis.opsForValue().set(String.format(FAVORITE_KEY_TEMPLATE, userId), value);
    }

    public void deleteFavoriteResult(String userId) {
        redis.delete(String.format(FAVORITE_KEY_TEMPLATE, userId));
    }

    public String getRecommendationKeywords(String userId) {
        return redis.opsForValue().get(String.format(RECOMMENDATION_KEY_TEMPLATE, userId));
    }

    public void setRecommendationKeywords(String userId, String keywords) {
        redis.opsForValue().set(String.format(RECOMMENDATION_KEY_TEMPLATE, userId), keywords);
    }

    public void deleteRecommendationKeywords(String userId) {
        redis.delete(String.format(RECOMMENDATION_KEY_TEMPLATE, userId));
    }

    public String getCorpusTotalItems() {
        return redis.opsForValue().get(CORPUS_TOTAL_ITEMS_KEY);
    }

    public void setCorpusTotalItems(String totalItems) {
        redis.opsForValue().set(CORPUS_TOTAL_ITEMS_KEY, totalItems);
    }

    public Map<String, Integer> getKeywordDocumentFrequencies(Set<String> keywords) {
        Map<String, Integer> frequencies = new HashMap<>();
        if (keywords.isEmpty()) {
            return frequencies;
        }

        List<Object> values = redis.opsForHash().multiGet(CORPUS_KEYWORD_DF_KEY, new ArrayList<>(keywords));
        int index = 0;
        for (String keyword : keywords) {
            Object value = values.get(index++);
            if (value != null) {
                frequencies.put(keyword, Integer.parseInt(value.toString()));
            }
        }
        return frequencies;
    }

    public void setKeywordDocumentFrequencies(Map<String, Integer> frequencies) {
        if (frequencies.isEmpty()) {
            return;
        }
        Map<String, String> hash = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            hash.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        redis.opsForHash().putAll(CORPUS_KEYWORD_DF_KEY, hash);
    }

    public void deleteCorpusCache() {
        redis.delete(CORPUS_TOTAL_ITEMS_KEY, CORPUS_KEYWORD_DF_KEY);
    }

    public void invalidateUserCaches(String userId) {
        deleteFavoriteResult(userId);
        deleteRecommendationKeywords(userId);
    }
}
