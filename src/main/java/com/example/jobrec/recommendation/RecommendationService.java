package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.SerpAPIClient;
import com.example.jobrec.service.ProductSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
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

    public List<Item> recommendItems(String userId, double lat, double lon) {
        MySQLConnection connection = new MySQLConnection();
        Set<String> favoritedItemIds = connection.getFavoriteItemIds(userId);
        connection.close();

        Map<String, Double> keywordWeights = profileService.getKeywordWeights(userId);
        if (keywordWeights.isEmpty()) {
            return recommendColdStart(favoritedItemIds);
        }

        RecommendationBuild build = buildPersonalizedRecommendations(
                keywordWeights, favoritedItemIds, lat, lon, properties.getMaxResults());

        if (shouldUseLowConfidenceFallback(build)) {
            List<Item> fallback = recommendPopularFallback(favoritedItemIds, properties.getColdStartFallbackSize());
            recordMetrics(fallback, build, false, true);
            return fallback;
        }

        recordMetrics(build.items, build, false, false);
        return build.items;
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

        RecommendationBuild build = buildPersonalizedRecommendations(
                keywordWeights, excludedItemIds, lat, lon, properties.getMaxResults());
        return build.items;
    }

    private RecommendationBuild buildPersonalizedRecommendations(Map<String, Double> keywordWeights,
                                                                 Set<String> excludedItemIds,
                                                                 double lat,
                                                                 double lon,
                                                                 int maxResults) {
        List<String> topKeywords = new ArrayList<>(keywordWeights.keySet());
        Set<String> visitedItemIds = new HashSet<>();
        int lowScoreFiltered = 0;

        List<Item> catalogCandidates = new ArrayList<>();
        for (Item item : productSearchService.searchCatalogByKeywords(topKeywords)) {
            if (excludedItemIds.contains(item.getId()) || !visitedItemIds.add(item.getId())) {
                continue;
            }
            if (itemRanker.scoreItem(item, keywordWeights) >= properties.getMinItemScore()) {
                catalogCandidates.add(item);
            } else {
                lowScoreFiltered++;
                visitedItemIds.remove(item.getId());
            }
        }

        List<Item> rankedCatalog = itemRanker.rankByScore(catalogCandidates, keywordWeights);
        List<Item> recommendedItems = new ArrayList<>();
        for (Item item : rankedCatalog) {
            if (recommendedItems.size() >= maxResults) {
                break;
            }
            recommendedItems.add(item);
        }

        int marketAdded = 0;
        int marketFiltered = 0;
        if (shouldSkipMarketSupplement(rankedCatalog.size())) {
            metrics.recordMarketSkippedForCatalogPriority();
        } else {
            int marketSlots = Math.min(properties.getMaxMarketResults(), maxResults - recommendedItems.size());
            List<Item> marketCandidates = new ArrayList<>();
            for (String keyword : topKeywords) {
                List<Item> marketItems = serpAPIClient.search(lat, lon, keyword);
                int addedForKeyword = 0;
                for (Item item : marketItems) {
                    if (addedForKeyword >= properties.getMarketSupplementPerKeyword()) {
                        break;
                    }
                    item.setSourceType(Item.SOURCE_MARKET);
                    if (excludedItemIds.contains(item.getId()) || !visitedItemIds.add(item.getId())) {
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
                if (marketAdded >= marketSlots || recommendedItems.size() >= maxResults) {
                    break;
                }
                if (itemRanker.scoreItem(item, keywordWeights) < properties.getMinItemScore()) {
                    lowScoreFiltered++;
                    continue;
                }
                recommendedItems.add(item);
                marketAdded++;
            }
        }

        return new RecommendationBuild(recommendedItems, rankedCatalog.size(), marketAdded,
                marketFiltered, lowScoreFiltered, keywordWeights);
    }

    private boolean shouldSkipMarketSupplement(int qualifiedCatalogCount) {
        return qualifiedCatalogCount >= properties.getSkipMarketWhenCatalogAtLeast();
    }

    private boolean shouldUseLowConfidenceFallback(RecommendationBuild build) {
        if (!properties.isLowConfidenceFallbackEnabled()) {
            return false;
        }
        if (build.items.size() < properties.getMinResultsBeforeFallback()) {
            return true;
        }
        return build.averageScore() < properties.getMinItemScore();
    }

    private List<Item> recommendColdStart(Set<String> favoritedItemIds) {
        if (!properties.isColdStartFallbackEnabled()) {
            metrics.recordRecommendation(0, 0, false, false, 0.0, 0);
            return new ArrayList<>();
        }
        List<Item> results = recommendPopularFallback(favoritedItemIds, properties.getColdStartFallbackSize());
        recordMetrics(results, null, true, false);
        return results;
    }

    private List<Item> recommendPopularFallback(Set<String> excludedItemIds, int limit) {
        MySQLConnection connection = new MySQLConnection();
        List<Item> popularItems = connection.getPopularCatalogItems(limit);
        connection.close();

        List<Item> results = new ArrayList<>();
        for (Item item : popularItems) {
            if (!excludedItemIds.contains(item.getId())) {
                results.add(item);
            }
        }
        return results;
    }

    private void recordMetrics(List<Item> items, RecommendationBuild build, boolean coldStart, boolean lowConfidence) {
        int catalogCount = (int) items.stream()
                .filter(item -> Item.SOURCE_INNOVA_CATALOG.equals(item.getSourceType()))
                .count();
        int marketCount = items.size() - catalogCount;
        double avgScore = build == null ? 0.0 : build.averageScore();
        int lowScoreFiltered = build == null ? 0 : build.lowScoreFiltered;
        metrics.recordRecommendation(catalogCount, marketCount, coldStart, lowConfidence, avgScore, lowScoreFiltered);
        if (build != null) {
            metrics.recordMarketFiltered(build.marketFiltered);
        }
    }

    private static final class RecommendationBuild {
        private final List<Item> items;
        private final int catalogCount;
        private final int marketCount;
        private final int marketFiltered;
        private final int lowScoreFiltered;
        private final Map<String, Double> keywordWeights;

        private RecommendationBuild(List<Item> items,
                                    int catalogCount,
                                    int marketCount,
                                    int marketFiltered,
                                    int lowScoreFiltered,
                                    Map<String, Double> keywordWeights) {
            this.items = items;
            this.catalogCount = catalogCount;
            this.marketCount = marketCount;
            this.marketFiltered = marketFiltered;
            this.lowScoreFiltered = lowScoreFiltered;
            this.keywordWeights = keywordWeights;
        }

        private double averageScore() {
            if (items.isEmpty()) {
                return 0.0;
            }
            ItemRanker ranker = new ItemRanker();
            return items.stream()
                    .mapToDouble(item -> ranker.scoreItem(item, keywordWeights))
                    .average()
                    .orElse(0.0);
        }
    }
}
