package com.example.jobrec.db;

import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisConnection {
    private static final String INSTANCE = "YOUR_REDIS_INSTANCE_IP";
    private static final int PORT = 6379;
    private static final String PASSWORD = "YOUR_PASSWORD";
    private static final String SEARCH_KEY_TEMPLATE = "search:lat=%s&lon=%s&keyword=%s";
    private static final String FAVORITE_KEY_TEMPLATE = "history:userId=%s";
    private static final String RECOMMENDATION_KEY_TEMPLATE = "recommendation:keywords:userId=%s";
    private static final String CORPUS_TOTAL_ITEMS_KEY = "corpus:total_items";
    private static final String CORPUS_KEYWORD_DF_KEY = "corpus:keyword_df";

    private Jedis jedis;

    public RedisConnection() {
        jedis = new Jedis(INSTANCE, PORT);
        jedis.auth(PASSWORD);
    }

    public void close() {
        jedis.close();
    }

    public String getSearchResult(double lat, double lon, String keyword) {
        if (jedis == null) {
            return null;
        }
        String key = String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword);
        return jedis.get(key);
    }

    public void setSearchResult(double lat, double lon, String keyword, String value) {
        if (jedis == null) {
            return;
        }
        String key = String.format(SEARCH_KEY_TEMPLATE, lat, lon, keyword);
        jedis.set(key, value);
        jedis.expire(key, 10);
    }

    public String getFavoriteResult(String userId) {
        if (jedis == null) {
            return null;
        }
        String key = String.format(FAVORITE_KEY_TEMPLATE, userId);
        return jedis.get(key);
    }

    public void setFavoriteResult(String userId, String value) {
        if (jedis == null) {
            return;
        }
        String key = String.format(FAVORITE_KEY_TEMPLATE, userId);
        jedis.set(key, value);
        jedis.expire(key, 10);
    }

    public void deleteFavoriteResult(String userId) {
        if (jedis == null) {
            return;
        }
        String key = String.format(FAVORITE_KEY_TEMPLATE, userId);
        jedis.del(key);
    }

    public String getRecommendationKeywords(String userId) {
        if (jedis == null) {
            return null;
        }
        return jedis.get(String.format(RECOMMENDATION_KEY_TEMPLATE, userId));
    }

    public void setRecommendationKeywords(String userId, String keywords, int ttlSeconds) {
        if (jedis == null) {
            return;
        }
        String key = String.format(RECOMMENDATION_KEY_TEMPLATE, userId);
        jedis.set(key, keywords);
        jedis.expire(key, ttlSeconds);
    }

    public void deleteRecommendationKeywords(String userId) {
        if (jedis == null) {
            return;
        }
        jedis.del(String.format(RECOMMENDATION_KEY_TEMPLATE, userId));
    }

    public String getCorpusTotalItems() {
        if (jedis == null) {
            return null;
        }
        return jedis.get(CORPUS_TOTAL_ITEMS_KEY);
    }

    public void setCorpusTotalItems(String totalItems) {
        if (jedis == null) {
            return;
        }
        jedis.set(CORPUS_TOTAL_ITEMS_KEY, totalItems);
    }

    public Map<String, Integer> getKeywordDocumentFrequencies(Set<String> keywords) {
        Map<String, Integer> frequencies = new HashMap<>();
        if (jedis == null || keywords.isEmpty()) {
            return frequencies;
        }

        String[] fields = keywords.toArray(new String[0]);
        List<String> values = jedis.hmget(CORPUS_KEYWORD_DF_KEY, fields);
        int index = 0;
        for (String keyword : keywords) {
            String value = values.get(index++);
            if (value != null) {
                frequencies.put(keyword, Integer.parseInt(value));
            }
        }
        return frequencies;
    }

    public void setKeywordDocumentFrequencies(Map<String, Integer> frequencies) {
        if (jedis == null || frequencies.isEmpty()) {
            return;
        }
        Map<String, String> hash = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            hash.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        jedis.hset(CORPUS_KEYWORD_DF_KEY, hash);
    }

    public void deleteCorpusCache() {
        if (jedis == null) {
            return;
        }
        jedis.del(CORPUS_TOTAL_ITEMS_KEY, CORPUS_KEYWORD_DF_KEY);
    }

    public static void main(String[] args) {
        RedisConnection c = new RedisConnection();
        c.setFavoriteResult("1234", "aaaa");
        System.out.println(c.getFavoriteResult("1234"));
        c.deleteFavoriteResult("1234");
        System.out.println(c.getFavoriteResult("1234"));
        c.setSearchResult(1, 2, "123", "aaa");
        System.out.println(c.getSearchResult(1, 2, "123"));
    }
}
