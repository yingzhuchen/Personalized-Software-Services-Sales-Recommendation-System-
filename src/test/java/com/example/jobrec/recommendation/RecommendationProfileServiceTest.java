package com.example.jobrec.recommendation;

import com.example.jobrec.config.RecommendationProperties;
import com.example.jobrec.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationProfileServiceTest {
    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private RecommendationProperties properties;

    @InjectMocks
    private RecommendationProfileService profileService;

    @Test
    void getTopKeywords_returnsCachedKeywordsWithoutDatabaseLookup() {
        when(redisCacheService.getRecommendationKeywords("user-1"))
                .thenReturn("crm:0.8,analytics:0.6,ai:0.4");

        List<String> keywords = profileService.getTopKeywords("user-1");

        assertEquals(Arrays.asList("crm", "analytics", "ai"), keywords);
    }

    @Test
    void getCachedSearchResult_readsRedisSearchCache() {
        when(redisCacheService.getSearchResult(1.0, 2.0, "crm")).thenReturn("[{\"id\":\"innova-crm\"}]");

        String cached = profileService.getCachedSearchResult(1.0, 2.0, "crm");

        assertEquals("[{\"id\":\"innova-crm\"}]", cached);
        verify(redisCacheService).getSearchResult(1.0, 2.0, "crm");
    }
}
