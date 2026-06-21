package com.example.jobrec.recommendation;

import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.db.RedisConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a user's TF-IDF profile using pre-aggregated stats and Redis cache-aside,
 * avoiding full-table scans on the keywords table at request time.
 */
public class RecommendationProfileService {
    private static final int TOP_KEYWORD_COUNT = 3;
    private static final int PROFILE_CACHE_TTL_SECONDS = 600;

    private final TFIDF tfidf = new TFIDF();

    public List<String> getTopKeywords(String userId) {
        RedisConnection redis = new RedisConnection();
        try {
            String cached = redis.getRecommendationKeywords(userId);
            if (cached != null && !cached.isEmpty()) {
                return parseKeywordList(cached);
            }

            MySQLConnection connection = new MySQLConnection();
            Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);
            Map<String, Integer> termFrequencies = connection.getTermFrequenciesForItems(favoritedItemIds);
            connection.close();

            if (termFrequencies.isEmpty()) {
                return new ArrayList<>();
            }

            int totalDocuments = getTotalDocuments(redis);
            Map<String, Integer> documentFrequencies = getDocumentFrequencies(redis, termFrequencies.keySet());

            Map<String, Double> tfidfScores = tfidf.computeScores(
                    termFrequencies, documentFrequencies, totalDocuments);
            List<Map.Entry<String, Double>> topKeywords =
                    tfidf.getTopKeywords(tfidfScores, TOP_KEYWORD_COUNT);

            List<String> keywords = new ArrayList<>();
            for (Map.Entry<String, Double> entry : topKeywords) {
                keywords.add(entry.getKey());
            }

            redis.setRecommendationKeywords(userId, String.join(",", keywords), PROFILE_CACHE_TTL_SECONDS);
            return keywords;
        } finally {
            redis.close();
        }
    }

    public static void invalidateUserProfile(String userId) {
        RedisConnection redis = new RedisConnection();
        try {
            redis.deleteRecommendationKeywords(userId);
        } finally {
            redis.close();
        }
    }

    public static void invalidateCorpusCache() {
        RedisConnection redis = new RedisConnection();
        try {
            redis.deleteCorpusCache();
        } finally {
            redis.close();
        }
    }

    private int getTotalDocuments(RedisConnection redis) {
        String cached = redis.getCorpusTotalItems();
        if (cached != null) {
            return Integer.parseInt(cached);
        }

        MySQLConnection connection = new MySQLConnection();
        int totalDocuments = connection.getTotalItemCount();
        connection.close();

        redis.setCorpusTotalItems(String.valueOf(totalDocuments));
        return totalDocuments;
    }

    private Map<String, Integer> getDocumentFrequencies(RedisConnection redis, Set<String> keywords) {
        Map<String, Integer> documentFrequencies = redis.getKeywordDocumentFrequencies(keywords);
        List<String> missingKeywords = new ArrayList<>();
        for (String keyword : keywords) {
            if (!documentFrequencies.containsKey(keyword)) {
                missingKeywords.add(keyword);
            }
        }

        if (!missingKeywords.isEmpty()) {
            MySQLConnection connection = new MySQLConnection();
            Map<String, Integer> loaded = connection.getDocumentFrequenciesForKeywords(missingKeywords);
            connection.close();
            documentFrequencies.putAll(loaded);
            redis.setKeywordDocumentFrequencies(loaded);
        }
        return documentFrequencies;
    }

    private List<String> parseKeywordList(String cached) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : cached.split(",")) {
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }
}
