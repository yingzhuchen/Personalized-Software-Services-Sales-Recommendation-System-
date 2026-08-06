package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.service.RedisCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendationProfileService {
    private final TFIDF tfidf = new TFIDF();
    private final RedisCacheService redisCacheService;
    private final RecommendationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecommendationProfileService(RedisCacheService redisCacheService,
                                        RecommendationProperties properties) {
        this.redisCacheService = redisCacheService;
        this.properties = properties;
    }

    public List<String> getTopKeywords(String userId) {
        return new ArrayList<>(getKeywordWeights(userId).keySet());
    }

    public Map<String, Double> getKeywordWeights(String userId) {
        String cached = redisCacheService.getRecommendationKeywords(userId);
        if (cached != null && !cached.isEmpty()) {
            return parseKeywordWeightMap(cached);
        }

        Map<String, Double> keywordWeights = computeKeywordWeights(userId);
        if (!keywordWeights.isEmpty()) {
            redisCacheService.setRecommendationKeywords(userId, serializeKeywordWeights(keywordWeights));
        }
        return keywordWeights;
    }

    public Map<String, Double> computeKeywordWeightsFromFavorites(Set<String> favoritedItemIds) {
        MySQLConnection connection = new MySQLConnection();
        Map<String, Integer> termFrequencies = connection.getTermFrequenciesForItems(favoritedItemIds);
        connection.close();

        if (termFrequencies.isEmpty()) {
            return new LinkedHashMap<>();
        }

        int totalDocuments = getTotalDocuments();
        Map<String, Integer> documentFrequencies = getDocumentFrequencies(termFrequencies.keySet());
        Map<String, Double> tfidfScores = tfidf.computeScores(
                termFrequencies, documentFrequencies, totalDocuments);

        Map<String, Double> keywordWeights = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : tfidf.getTopKeywords(
                tfidfScores, properties.getTopKeywordCount())) {
            keywordWeights.put(entry.getKey(), entry.getValue());
        }
        return keywordWeights;
    }

    public void invalidateCorpusCache() {
        redisCacheService.deleteCorpusCache();
    }

    public String getCachedSearchResult(double lat, double lon, String keyword) {
        return redisCacheService.getSearchResult(lat, lon, keyword);
    }

    public void cacheSearchResult(double lat, double lon, String keyword, List<Item> items) {
        try {
            redisCacheService.setSearchResult(lat, lon, keyword, objectMapper.writeValueAsString(items));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Item> parseItems(String cachedResult) {
        try {
            return Arrays.asList(objectMapper.readValue(cachedResult, Item[].class));
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private Map<String, Double> computeKeywordWeights(String userId) {
        MySQLConnection connection = new MySQLConnection();
        Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);
        connection.close();
        return computeKeywordWeightsFromFavorites(favoritedItemIds);
    }

    private int getTotalDocuments() {
        String cached = redisCacheService.getCorpusTotalItems();
        if (cached != null) {
            return Integer.parseInt(cached);
        }

        MySQLConnection connection = new MySQLConnection();
        int totalDocuments = connection.getTotalItemCount();
        connection.close();

        redisCacheService.setCorpusTotalItems(String.valueOf(totalDocuments));
        return totalDocuments;
    }

    private Map<String, Integer> getDocumentFrequencies(Set<String> keywords) {
        Map<String, Integer> documentFrequencies = redisCacheService.getKeywordDocumentFrequencies(keywords);
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
            redisCacheService.setKeywordDocumentFrequencies(loaded);
        }
        return documentFrequencies;
    }

    private Map<String, Double> parseKeywordWeightMap(String cached) {
        Map<String, Double> keywordWeights = new LinkedHashMap<>();
        for (String token : cached.split(",")) {
            if (token.isEmpty()) {
                continue;
            }
            int separator = token.indexOf(':');
            if (separator <= 0 || separator == token.length() - 1) {
                keywordWeights.put(token, 1.0);
                continue;
            }
            keywordWeights.put(token.substring(0, separator), Double.parseDouble(token.substring(separator + 1)));
        }
        return keywordWeights;
    }

    private List<String> parseKeywordList(String cached) {
        return new ArrayList<>(parseKeywordWeightMap(cached).keySet());
    }

    private String serializeKeywordWeights(Map<String, Double> keywordWeights) {
        List<String> tokens = new ArrayList<>();
        for (Map.Entry<String, Double> entry : keywordWeights.entrySet()) {
            tokens.add(entry.getKey() + ":" + entry.getValue());
        }
        return String.join(",", tokens);
    }
}
