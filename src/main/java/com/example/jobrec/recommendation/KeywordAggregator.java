package com.example.jobrec.recommendation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates favorited-item keywords by frequency and returns the Top-K signals
 * used to drive content-based external product/service search.
 */
public class KeywordAggregator {
    public static final int DEFAULT_TOP_K = 3;

    public Map<String, Integer> aggregate(Map<String, Set<String>> itemKeywords) {
        Map<String, Integer> counts = new HashMap<>();
        if (itemKeywords == null) {
            return counts;
        }
        for (Set<String> keywords : itemKeywords.values()) {
            if (keywords == null) {
                continue;
            }
            for (String keyword : keywords) {
                if (keyword == null || keyword.isEmpty()) {
                    continue;
                }
                counts.put(keyword, counts.getOrDefault(keyword, 0) + 1);
            }
        }
        return counts;
    }

    public List<String> topKeywords(Map<String, Integer> keywordCounts, int topK) {
        List<String> result = new ArrayList<>();
        if (keywordCounts == null || keywordCounts.isEmpty() || topK <= 0) {
            return result;
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(keywordCounts.entrySet());
        ranked.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));

        int limit = Math.min(topK, ranked.size());
        for (int i = 0; i < limit; i++) {
            result.add(ranked.get(i).getKey());
        }
        return result;
    }

    public List<String> topKeywords(Map<String, Integer> keywordCounts) {
        return topKeywords(keywordCounts, DEFAULT_TOP_K);
    }
}
