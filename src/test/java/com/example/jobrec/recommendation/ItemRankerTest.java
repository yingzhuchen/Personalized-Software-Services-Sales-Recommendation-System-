package com.example.jobrec.recommendation;

import com.example.jobrec.entity.Item;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRankerTest {
    private final ItemRanker itemRanker = new ItemRanker();

    @Test
    void rankByScore_ordersByWeightedKeywordOverlap() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("crm", 0.9);
        weights.put("analytics", 0.4);

        Item high = new Item("high", "High", "seller", "$1", "source", Item.SOURCE_INNOVA_CATALOG,
                "desc", null, "url", new HashSet<>(Arrays.asList("crm", "analytics")), false);
        Item low = new Item("low", "Low", "seller", "$1", "source", Item.SOURCE_INNOVA_CATALOG,
                "desc", null, "url", new HashSet<>(Arrays.asList("analytics")), false);

        List<Item> ranked = itemRanker.rankByScore(Arrays.asList(low, high), weights);

        assertEquals("high", ranked.get(0).getId());
        assertEquals("low", ranked.get(1).getId());
    }

    @Test
    void rankByScore_breaksTiesByItemId() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("crm", 0.5);

        Item b = new Item("b", "B", "seller", "$1", "source", Item.SOURCE_INNOVA_CATALOG,
                "desc", null, "url", new HashSet<>(Arrays.asList("crm")), false);
        Item a = new Item("a", "A", "seller", "$1", "source", Item.SOURCE_INNOVA_CATALOG,
                "desc", null, "url", new HashSet<>(Arrays.asList("crm")), false);

        List<Item> ranked = itemRanker.rankByScore(Arrays.asList(b, a), weights);

        assertEquals("a", ranked.get(0).getId());
        assertEquals("b", ranked.get(1).getId());
    }

    @Test
    void countKeywordOverlap_requiresSharedKeywordsForMarketRelevance() {
        Item item = new Item("m1", "Market", "seller", "$1", "source", Item.SOURCE_MARKET,
                "desc", null, "url", new HashSet<>(Arrays.asList("crm", "sales")), false);

        assertTrue(itemRanker.countKeywordOverlap(item, new HashSet<>(Arrays.asList("crm"))) >= 1);
        assertEquals(0, itemRanker.countKeywordOverlap(item, new HashSet<>(Arrays.asList("ai"))));
    }
}
