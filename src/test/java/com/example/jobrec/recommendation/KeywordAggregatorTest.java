package com.example.jobrec.recommendation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordAggregatorTest {
    private final KeywordAggregator aggregator = new KeywordAggregator();

    @Test
    void aggregate_countsKeywordsAcrossFavoritedItems() {
        Map<String, Set<String>> itemKeywords = new HashMap<>();
        itemKeywords.put("p1", new HashSet<>(Arrays.asList("crm", "sales")));
        itemKeywords.put("p2", new HashSet<>(Arrays.asList("crm", "analytics")));
        itemKeywords.put("p3", new HashSet<>(Arrays.asList("crm")));

        Map<String, Integer> counts = aggregator.aggregate(itemKeywords);

        assertEquals(3, counts.get("crm"));
        assertEquals(1, counts.get("sales"));
        assertEquals(1, counts.get("analytics"));
    }

    @Test
    void topKeywords_returnsTopThreeByFrequency() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("crm", 6);
        counts.put("analytics", 4);
        counts.put("ai", 3);
        counts.put("remote", 1);

        List<String> top = aggregator.topKeywords(counts);

        assertEquals(Arrays.asList("crm", "analytics", "ai"), top);
    }

    @Test
    void topKeywords_returnsEmptyWhenNoFavorites() {
        assertTrue(aggregator.topKeywords(new HashMap<>()).isEmpty());
        assertTrue(aggregator.aggregate(null).isEmpty());
    }
}
