package com.example.jobrec.recommendation;

import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.SerpAPIClient;
import com.example.jobrec.service.RedisCacheService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecommendationService {
    private final RecommendationProfileService profileService;
    private final SerpAPIClient serpAPIClient = new SerpAPIClient();

    public RecommendationService(RecommendationProfileService profileService) {
        this.profileService = profileService;
    }

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
        for (String keyword : topKeywords) {
            List<Item> items = serpAPIClient.search(lat, lon, keyword);
            for (Item item : items) {
                if (!favoritedItemIds.contains(item.getId()) && !visitedItemIds.contains(item.getId())) {
                    recommendedItems.add(item);
                    visitedItemIds.add(item.getId());
                }
            }
        }
        return recommendedItems;
    }

    public List<Item> searchJobs(double lat, double lon) {
        String cachedResult = profileService.getCachedSearchResult(lat, lon);
        if (cachedResult != null) {
            return profileService.parseItems(cachedResult);
        }

        List<Item> items = serpAPIClient.search(lat, lon, null);
        profileService.cacheSearchResult(lat, lon, items);
        return items;
    }
}
