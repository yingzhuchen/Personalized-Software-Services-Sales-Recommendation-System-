package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.SerpAPIClient;
import com.example.jobrec.service.ProductSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final RecommendationProfileService profileService;
    private final ProductSearchService productSearchService;
    private final RecommendationProperties properties;
    private final RecommendationMetrics metrics;
    private final ItemRanker itemRanker = new ItemRanker();
    private final SerpAPIClient serpAPIClient = new SerpAPIClient();

    public RecommendationService(RecommendationProfileService profileService,
                                 ProductSearchService productSearchService,
                                 RecommendationProperties properties,
                                 RecommendationMetrics metrics) {
        this.profileService = profileService;
        this.productSearchService = productSearchService;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Recommendations prioritize INNOVA catalog products ranked by TF-IDF-weighted keyword overlap.
     * SerpAPI market data supplements thin coverage after a minimum keyword-overlap filter.
     */
    public List<Item> recommendItems(String userId, double lat, double lon) {
        MySQLConnection connection = new MySQLConnection();
        Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);
        connection.close();

        Map<String, Double> keywordWeights = profileService.getKeywordWeights(userId);
        if (keywordWeights.isEmpty()) {
            return recommendColdStart(favoritedItemIds);
        }

        List<String> topKeywords = new ArrayList<>(keywordWeights.keySet());
        Set<String> visitedItemIds = new HashSet<>();
        List<Item> catalogCandidates = new ArrayList<>();

        List<Item> catalogMatches = productSearchService.searchCatalogByKeywords(topKeywords);
        for (Item item : catalogMatches) {
            if (!favoritedItemIds.contains(item.getId()) && visitedItemIds.add(item.getId())) {
                catalogCandidates.add(item);
            }
        }

        List<Item> rankedCatalog = itemRanker.rankByScore(catalogCandidates, keywordWeights);
        List<Item> recommendedItems = new ArrayList<>(rankedCatalog);

        int marketAdded = 0;
        int marketFiltered = 0;
        List<Item> marketCandidates = new ArrayList<>();
        for (String keyword : topKeywords) {
            List<Item> marketItems = serpAPIClient.search(lat, lon, keyword);
            int addedForKeyword = 0;
            for (Item item : marketItems) {
                if (addedForKeyword >= properties.getMarketSupplementPerKeyword()) {
                    break;
                }
                item.setSourceType(Item.SOURCE_MARKET);
                if (favoritedItemIds.contains(item.getId()) || !visitedItemIds.add(item.getId())) {
                    continue;
                }
                if (itemRanker.countKeywordOverlap(item, keywordWeights.keySet())
                        < properties.getMarketMinKeywordOverlap()) {
                    marketFiltered++;
                    visitedItemIds.remove(item.getId());
                    continue;
                }
                marketCandidates.add(item);
                addedForKeyword++;
            }
        }

        List<Item> rankedMarket = itemRanker.rankByScore(marketCandidates, keywordWeights);
        for (Item item : rankedMarket) {
            if (recommendedItems.size() >= properties.getMaxResults()) {
                break;
            }
            recommendedItems.add(item);
            marketAdded++;
        }

        if (recommendedItems.size() > properties.getMaxResults()) {
            recommendedItems = new ArrayList<>(recommendedItems.subList(0, properties.getMaxResults()));
        }

        int catalogCount = (int) recommendedItems.stream()
                .filter(item -> Item.SOURCE_INNOVA_CATALOG.equals(item.getSourceType()))
                .count();
        metrics.recordRecommendation(catalogCount, marketAdded, false);
        metrics.recordMarketFiltered(marketFiltered);
        return recommendedItems;
    }

    public List<Item> searchProducts(double lat, double lon, String keyword) {
        String cacheKey = keyword == null ? "" : keyword;
        String cachedResult = profileService.getCachedSearchResult(lat, lon, cacheKey);
        if (cachedResult != null) {
            return profileService.parseItems(cachedResult);
        }

        List<Item> items = productSearchService.search(lat, lon, keyword);
        profileService.cacheSearchResult(lat, lon, cacheKey, items);
        return items;
    }

    public List<String> recommendItemIds(String userId, double lat, double lon) {
        return recommendItems(userId, lat, lon).stream()
                .map(Item::getId)
                .collect(Collectors.toList());
    }

    public List<Item> recommendItemsForEvaluation(Set<String> trainingFavorites,
                                                  Set<String> excludedItemIds,
                                                  double lat,
                                                  double lon) {
        Map<String, Double> keywordWeights = profileService.computeKeywordWeightsFromFavorites(trainingFavorites);
        if (keywordWeights.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> topKeywords = new ArrayList<>(keywordWeights.keySet());
        Set<String> visitedItemIds = new HashSet<>();
        List<Item> catalogCandidates = new ArrayList<>();
        List<Item> catalogMatches = productSearchService.searchCatalogByKeywords(topKeywords);
        for (Item item : catalogMatches) {
            if (!excludedItemIds.contains(item.getId()) && visitedItemIds.add(item.getId())) {
                catalogCandidates.add(item);
            }
        }

        List<Item> recommendedItems = new ArrayList<>(
                itemRanker.rankByScore(catalogCandidates, keywordWeights));

        for (String keyword : topKeywords) {
            if (recommendedItems.size() >= properties.getMaxResults()) {
                break;
            }
            List<Item> marketItems = serpAPIClient.search(lat, lon, keyword);
            int addedForKeyword = 0;
            for (Item item : marketItems) {
                if (addedForKeyword >= properties.getMarketSupplementPerKeyword()
                        || recommendedItems.size() >= properties.getMaxResults()) {
                    break;
                }
                item.setSourceType(Item.SOURCE_MARKET);
                if (excludedItemIds.contains(item.getId()) || !visitedItemIds.add(item.getId())) {
                    continue;
                }
                if (itemRanker.countKeywordOverlap(item, keywordWeights.keySet())
                        < properties.getMarketMinKeywordOverlap()) {
                    visitedItemIds.remove(item.getId());
                    continue;
                }
                recommendedItems.add(item);
                addedForKeyword++;
            }
        }

        if (recommendedItems.size() > properties.getMaxResults()) {
            return new ArrayList<>(recommendedItems.subList(0, properties.getMaxResults()));
        }
        return recommendedItems;
    }

    private List<Item> recommendColdStart(Set<String> favoritedItemIds) {
        if (!properties.isColdStartFallbackEnabled()) {
            metrics.recordRecommendation(0, 0, false);
            return new ArrayList<>();
        }

        MySQLConnection connection = new MySQLConnection();
        List<Item> popularItems = connection.getPopularCatalogItems(properties.getColdStartFallbackSize());
        connection.close();

        List<Item> results = new ArrayList<>();
        for (Item item : popularItems) {
            if (!favoritedItemIds.contains(item.getId())) {
                results.add(item);
            }
        }

        metrics.recordRecommendation(results.size(), 0, true);
        return results;
    }
}
