package com.example.jobrec.recommendation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TFIDFTest {
    private final TFIDF tfidf = new TFIDF();

    @Test
    void computeScores_ranksRareKeywordHigherThanCommonKeyword() {
        Map<String, Integer> termFrequencies = new HashMap<>();
        termFrequencies.put("crm", 3);
        termFrequencies.put("software", 1);

        Map<String, Integer> documentFrequencies = new HashMap<>();
        documentFrequencies.put("crm", 2);
        documentFrequencies.put("software", 10);

        Map<String, Double> scores = tfidf.computeScores(termFrequencies, documentFrequencies, 10);

        assertTrue(scores.get("crm") > scores.get("software"));
    }

    @Test
    void computeScores_returnsEmptyMapWhenNoTerms() {
        Map<String, Double> scores = tfidf.computeScores(new HashMap<>(), new HashMap<>(), 5);
        assertTrue(scores.isEmpty());
    }

    @Test
    void computeScores_returnsEmptyMapWhenTotalDocumentsIsZero() {
        Map<String, Integer> termFrequencies = new HashMap<>();
        termFrequencies.put("crm", 1);
        Map<String, Double> scores = tfidf.computeScores(termFrequencies, new HashMap<>(), 0);
        assertTrue(scores.isEmpty());
    }

    @Test
    void getTopKeywords_returnsTopThreeByScore() {
        Map<String, Double> scores = new HashMap<>();
        scores.put("ai", 0.9);
        scores.put("crm", 0.7);
        scores.put("cloud", 0.5);
        scores.put("security", 0.2);

        List<Map.Entry<String, Double>> top = tfidf.getTopKeywords(scores, 3);

        assertEquals(3, top.size());
        assertEquals("ai", top.get(0).getKey());
        assertEquals("crm", top.get(1).getKey());
        assertEquals("cloud", top.get(2).getKey());
    }
}
