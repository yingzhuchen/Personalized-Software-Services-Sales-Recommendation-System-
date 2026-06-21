package com.example.jobrec.recommendation;

import com.example.jobrec.external.SerpAPIClient;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Recommendation {
    private final RecommendationProfileService profileService = new RecommendationProfileService();

    public List<Item> recommendItems(String userId, double lat, double lon) {
        List<Item> recommendedItems = new ArrayList<>();

        MySQLConnection connection = new MySQLConnection();
        Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);
        connection.close();

        List<String> topKeywords = profileService.getTopKeywords(userId);
        if (topKeywords.isEmpty()) {
            return recommendedItems;
        }

        Set<String> visitedItemIds = new HashSet<>();
        SerpAPIClient client = new SerpAPIClient();
        for (String keyword : topKeywords) {
            List<Item> items = client.search(lat, lon, keyword);
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
