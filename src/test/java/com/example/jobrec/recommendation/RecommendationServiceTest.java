package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import com.example.jobrec.db.MySQLConnection;
import com.example.jobrec.entity.Item;
import com.example.jobrec.external.SerpAPIClient;
import com.example.jobrec.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock
    private RecommendationProfileService profileService;

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private RecommendationProperties properties;

    @Mock
    private RecommendationMetrics metrics;

    @InjectMocks
    private RecommendationService recommendationService;

    private void stubRecommendationDefaults() {
        lenient().when(properties.getMarketSupplementPerKeyword()).thenReturn(3);
        lenient().when(properties.getMaxResults()).thenReturn(50);
        lenient().when(properties.getMarketMinKeywordOverlap()).thenReturn(1);
        lenient().when(properties.getMinItemScore()).thenReturn(0.0);
        lenient().when(properties.getMinResultsBeforeFallback()).thenReturn(1);
        lenient().when(properties.isLowConfidenceFallbackEnabled()).thenReturn(false);
        lenient().when(properties.getMaxMarketResults()).thenReturn(5);
        lenient().when(properties.getSkipMarketWhenCatalogAtLeast()).thenReturn(3);
    }

    @Test
    void recommendItems_returnsEmptyListWhenUserHasNoKeywordsAndColdStartDisabled() {
        when(properties.isColdStartFallbackEnabled()).thenReturn(false);

        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> when(mock.getFavoriteItemIds("user-1")).thenReturn(Collections.emptySet()))) {

            when(profileService.getKeywordWeights("user-1")).thenReturn(Collections.emptyMap());

            List<Item> results = recommendationService.recommendItems("user-1", 37.4, -122.1);

            assertTrue(results.isEmpty());
            verify(metrics).recordRecommendation(0, 0, false, false, 0.0, 0);
        }
    }

    @Test
    void recommendItems_usesColdStartFallbackWhenProfileIsEmpty() {
        Item popular = new Item(
                "innova-crm",
                "INNOVA CRM Platform",
                "INNOVA AI",
                "$99/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "CRM",
                null,
                "https://innova.ai/products/crm",
                new HashSet<>(Collections.singletonList("crm")),
                false);

        when(properties.isColdStartFallbackEnabled()).thenReturn(true);
        when(properties.getColdStartFallbackSize()).thenReturn(5);

        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> {
                    when(mock.getFavoriteItemIds("user-1")).thenReturn(Collections.emptySet());
                    when(mock.getPopularCatalogItems(5)).thenReturn(Collections.singletonList(popular));
                })) {

            when(profileService.getKeywordWeights("user-1")).thenReturn(Collections.emptyMap());

            List<Item> results = recommendationService.recommendItems("user-1", 37.4, -122.1);

            assertEquals(1, results.size());
            assertEquals("innova-crm", results.get(0).getId());
            verify(metrics).recordRecommendation(1, 0, true, false, 0.0, 0);
        }
    }

    @Test
    void recommendItems_prioritizesCatalogMatchesAndSkipsFavoritedItems() {
        stubRecommendationDefaults();
        Item catalogItem = new Item(
                "innova-analytics",
                "INNOVA Analytics Suite",
                "INNOVA AI",
                "$199/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "Analytics",
                null,
                "https://innova.ai/products/analytics",
                new HashSet<>(Collections.singletonList("analytics")),
                false);

        Map<String, Double> keywordWeights = new HashMap<>();
        keywordWeights.put("analytics", 0.9);
        keywordWeights.put("crm", 0.4);

        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> when(mock.getFavoriteItemIds("user-1"))
                        .thenReturn(new HashSet<>(Collections.singletonList("innova-crm"))));
             MockedConstruction<SerpAPIClient> serp = mockConstruction(SerpAPIClient.class,
                     (mock, context) -> when(mock.search(any(), any(), anyString()))
                             .thenReturn(Collections.emptyList()))) {

            when(profileService.getKeywordWeights("user-1")).thenReturn(keywordWeights);
            when(productSearchService.searchCatalogByKeywords(Arrays.asList("analytics", "crm")))
                    .thenReturn(Collections.singletonList(catalogItem));

            List<Item> results = recommendationService.recommendItems("user-1", 37.4, -122.1);

            assertEquals(1, results.size());
            assertEquals("innova-analytics", results.get(0).getId());
            assertEquals(Item.SOURCE_INNOVA_CATALOG, results.get(0).getSourceType());
            verify(metrics).recordRecommendation(1, 0, false, false, 0.9, 0);
        }
    }

    @Test
    void recommendItems_fallsBackWhenScoresAreTooLow() {
        stubRecommendationDefaults();
        when(properties.isLowConfidenceFallbackEnabled()).thenReturn(true);
        when(properties.getMinItemScore()).thenReturn(0.5);
        when(properties.getMinResultsBeforeFallback()).thenReturn(3);
        when(properties.getColdStartFallbackSize()).thenReturn(2);

        Item weakMatch = new Item(
                "innova-analytics",
                "INNOVA Analytics Suite",
                "INNOVA AI",
                "$199/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "Analytics",
                null,
                "https://innova.ai/products/analytics",
                new HashSet<>(Collections.singletonList("analytics")),
                false);
        Item popular = new Item(
                "innova-crm",
                "INNOVA CRM Platform",
                "INNOVA AI",
                "$99/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "CRM",
                null,
                "https://innova.ai/products/crm",
                new HashSet<>(Collections.singletonList("crm")),
                false);

        Map<String, Double> keywordWeights = new HashMap<>();
        keywordWeights.put("analytics", 0.1);

        try (MockedConstruction<MySQLConnection> mysql = mockConstruction(MySQLConnection.class,
                (mock, context) -> {
                    when(mock.getFavoriteItemIds("user-1")).thenReturn(Collections.emptySet());
                    when(mock.getPopularCatalogItems(2)).thenReturn(Collections.singletonList(popular));
                });
             MockedConstruction<SerpAPIClient> serp = mockConstruction(SerpAPIClient.class,
                     (mock, context) -> when(mock.search(any(), any(), anyString()))
                             .thenReturn(Collections.emptyList()))) {

            when(profileService.getKeywordWeights("user-1")).thenReturn(keywordWeights);
            when(productSearchService.searchCatalogByKeywords(Collections.singletonList("analytics")))
                    .thenReturn(Collections.singletonList(weakMatch));

            List<Item> results = recommendationService.recommendItems("user-1", 37.4, -122.1);

            assertEquals(1, results.size());
            assertEquals("innova-crm", results.get(0).getId());
            verify(metrics).recordRecommendation(1, 0, false, true, 0.0, 1);
        }
    }

    @Test
    void searchProducts_returnsCachedResultsWhenPresent() {
        Item cachedItem = new Item(
                "innova-crm",
                "INNOVA CRM Platform",
                "INNOVA AI",
                "$99/month",
                "INNOVA AI",
                Item.SOURCE_INNOVA_CATALOG,
                "CRM",
                null,
                "https://innova.ai/products/crm",
                new HashSet<>(Collections.singletonList("crm")),
                false);
        String cachedJson = "[{\"id\":\"innova-crm\",\"title\":\"INNOVA CRM Platform\",\"source_type\":\"innova_catalog\",\"favorite\":false}]";
        when(profileService.getCachedSearchResult(1.0, 2.0, "crm")).thenReturn(cachedJson);
        when(profileService.parseItems(cachedJson)).thenReturn(Collections.singletonList(cachedItem));

        List<Item> results = recommendationService.searchProducts(1.0, 2.0, "crm");

        assertEquals(1, results.size());
        assertEquals("innova-crm", results.get(0).getId());
    }
}
