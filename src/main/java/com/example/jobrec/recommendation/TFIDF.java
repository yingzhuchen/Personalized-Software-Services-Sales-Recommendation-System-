package com.example.jobrec.recommendation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TFIDF {

    /**
     * Computes TF-IDF scores for terms in a user profile.
     *
     * TF  = term count in user's favorites / total term count in user's favorites
     * IDF = log((N + 1) / (df + 1)) + 1   (smoothed to avoid division by zero)
     */
    public Map<String, Double> computeScores(Map<String, Integer> termFrequencies,
                                             Map<String, Integer> documentFrequencies,
                                             int totalDocuments) {
        Map<String, Double> scores = new HashMap<>();
        if (termFrequencies.isEmpty() || totalDocuments == 0) {
            return scores;
        }

        int totalTerms = termFrequencies.values().stream().mapToInt(Integer::intValue).sum();
        if (totalTerms == 0) {
            return scores;
        }

        for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
            String term = entry.getKey();
            double tf = (double) entry.getValue() / totalTerms;
            int df = documentFrequencies.getOrDefault(term, 0);
            double idf = Math.log((totalDocuments + 1.0) / (df + 1.0)) + 1.0;
            scores.put(term, tf * idf);
        }
        return scores;
    }

    public List<Map.Entry<String, Double>> getTopKeywords(Map<String, Double> scores, int topK) {
        List<Map.Entry<String, Double>> keywordList = new ArrayList<>(scores.entrySet());
        keywordList.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));
        if (keywordList.size() > topK) {
            return keywordList.subList(0, topK);
        }
        return keywordList;
    }
}
