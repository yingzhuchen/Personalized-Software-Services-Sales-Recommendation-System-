package com.example.jobrec.recommendation;

import com.example.jobrec.entity.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores and ranks catalog/market items by TF-IDF-weighted keyword overlap.
 */
public class ItemRanker {

    public double scoreItem(Item item, Map<String, Double> keywordWeights) {
        if (item == null || keywordWeights == null || keywordWeights.isEmpty()) {
            return 0.0;
        }
        Set<String> itemKeywords = item.getKeywords();
        if (itemKeywords == null || itemKeywords.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        for (String keyword : itemKeywords) {
            score += keywordWeights.getOrDefault(keyword, 0.0);
        }
        return score;
    }

    public int countKeywordOverlap(Item item, Set<String> keywords) {
        if (item == null || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        Set<String> itemKeywords = item.getKeywords();
        if (itemKeywords == null || itemKeywords.isEmpty()) {
            return 0;
        }
        int overlap = 0;
        for (String keyword : itemKeywords) {
            if (keywords.contains(keyword)) {
                overlap++;
            }
        }
        return overlap;
    }

    public List<Item> rankByScore(List<Item> items, Map<String, Double> keywordWeights) {
        List<Item> ranked = new ArrayList<>(items);
        ranked.sort((left, right) -> {
            int byScore = Double.compare(scoreItem(right, keywordWeights), scoreItem(left, keywordWeights));
            if (byScore != 0) {
                return byScore;
            }
            String leftId = left.getId() == null ? "" : left.getId();
            String rightId = right.getId() == null ? "" : right.getId();
            return leftId.compareTo(rightId);
        });
        return ranked;
    }
}
