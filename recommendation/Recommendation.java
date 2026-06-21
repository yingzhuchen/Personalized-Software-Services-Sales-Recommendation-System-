package com.example.jobrec.recommendation;

import com.example.jobrec.external.SerpAPIClient;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;

import java.util.*;

public class Recommendation {
    private static final int TOP_KEYWORD_COUNT = 3;

    public List<Item> recommendItems(String userId, double lat, double lon) {
        List<Item> recommendedItems = new ArrayList<>();

        MySQLConnection connection = new MySQLConnection();
        Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);

        Map<String, Integer> termFrequencies = new HashMap<>();
        for (String itemId : favoritedItemIds) {
            Set<String> keywords = connection.getKeywords(itemId);
            for (String keyword : keywords) {
                termFrequencies.put(keyword, termFrequencies.getOrDefault(keyword, 0) + 1);
            }
        }

        int totalDocuments = connection.getTotalItemCount();
        Map<String, Integer> documentFrequencies = connection.getKeywordDocumentFrequencies();
        connection.close();

        TFIDF tfidf = new TFIDF();
        Map<String, Double> tfidfScores = tfidf.computeScores(termFrequencies, documentFrequencies, totalDocuments);
        List<Map.Entry<String, Double>> keywordList = tfidf.getTopKeywords(tfidfScores, TOP_KEYWORD_COUNT);

        Set<String> visitedItemIds = new HashSet<>();
        SerpAPIClient client = new SerpAPIClient();
        for (Map.Entry<String, Double> keyword : keywordList) {
            List<Item> items = client.search(lat, lon, keyword.getKey());

            for (Item item : items) {
                if (!favoritedItemIds.contains(item.getId()) && !visitedItemIds.contains(item.getId())) {
                    recommendedItems.add(item);
                    visitedItemIds.add(item.getId());
                }
            }
        }
        return recommendedItems;
    }
}

