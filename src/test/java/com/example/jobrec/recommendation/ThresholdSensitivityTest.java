package com.example.jobrec.recommendation;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simulates top-K keyword and market-supplement thresholds against the seeded INNOVA catalog.
 * No live SerpAPI calls — compares result volume, API cost, and TF-IDF score drop-off.
 */
class ThresholdSensitivityTest {

    private static final int CATALOG_SIZE = 5;
    private static final Map<String, Set<String>> CATALOG_KEYWORDS = Map.of(
            "innova-crm", Set.of("crm", "sales", "customer", "saas"),
            "innova-analytics", Set.of("analytics", "dashboard", "bi", "data"),
            "innova-ai-platform", Set.of("ai", "nlp", "ml", "recommendation"),
            "innova-cloud-migrate", Set.of("cloud", "migration", "aws", "enterprise"),
            "innova-security", Set.of("security", "compliance", "enterprise", "software")
    );

    /** User favorited CRM + Analytics — realistic warm profile. */
    private static final Set<String> FAVORITED = Set.of("innova-crm", "innova-analytics");

    private final TFIDF tfidf = new TFIDF();

    @Test
    void compareTopKeywordThresholds() {
        Map<String, Integer> termFreq = aggregateTermFrequencies(FAVORITED);
        Map<String, Integer> docFreq = buildDocumentFrequencies();

        Map<String, Double> scores = tfidf.computeScores(termFreq, docFreq, CATALOG_SIZE);
        double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();

        System.out.println("\n=== Top-K keyword threshold (market cap = 3 per keyword) ===");
        System.out.printf("%-4s %-8s %-12s %-14s %-12s%n",
                "K", "Keywords", "Score%", "CatalogHits", "MaxSerpCalls");
        for (int k : List.of(3, 5, 8)) {
            List<Map.Entry<String, Double>> top = tfidf.getTopKeywords(scores, k);
            double covered = top.stream().mapToDouble(Map.Entry::getValue).sum();
            int catalogHits = countCatalogMatches(top);
            int maxSerp = k * 3;
            System.out.printf("%-4d %-8s %-11.1f%% %-14d %-12d%n",
                    k,
                    formatKeywords(top),
                    100.0 * covered / totalScore,
                    catalogHits,
                    maxSerp);
        }
        assertTrue(scores.size() >= 3);
    }

    @Test
    void compareMarketSupplementPerKeyword() {
        int topK = 3;
        Map<String, Integer> termFreq = aggregateTermFrequencies(FAVORITED);
        Map<String, Integer> docFreq = buildDocumentFrequencies();
        Map<String, Double> scores = tfidf.computeScores(termFreq, docFreq, CATALOG_SIZE);
        List<Map.Entry<String, Double>> top = tfidf.getTopKeywords(scores, topK);

        System.out.println("\n=== Market supplement per keyword (topK = 3) ===");
        System.out.printf("%-6s %-14s %-12s%n", "Cap", "MaxMarketItems", "MaxSerpCalls");
        for (int cap : List.of(3, 5, 8)) {
            System.out.printf("%-6d %-14d %-12d%n", cap, topK * cap, topK * cap);
        }
        assertTrue(top.size() == 3);
    }

    @Test
    void noteSearchEndpointUsesFive() {
        // ProductSearchService.MARKET_SUPPLEMENT_LIMIT = 5 for /search, but recommendation uses 3.
        System.out.println("\n=== Existing inconsistency ===");
        System.out.println("GET /search          → market cap = 5 (ProductSearchService)");
        System.out.println("GET /recommendation  → topK = 3, market cap = 3 per keyword");
    }

    private Map<String, Integer> aggregateTermFrequencies(Set<String> itemIds) {
        Map<String, Integer> freq = new HashMap<>();
        for (String itemId : itemIds) {
            for (String kw : CATALOG_KEYWORDS.get(itemId)) {
                freq.merge(kw, 1, Integer::sum);
            }
        }
        return freq;
    }

    private Map<String, Integer> buildDocumentFrequencies() {
        Map<String, Integer> df = new HashMap<>();
        for (Set<String> keywords : CATALOG_KEYWORDS.values()) {
            for (String kw : keywords) {
                df.merge(kw, 1, Integer::sum);
            }
        }
        return df;
    }

    private int countCatalogMatches(List<Map.Entry<String, Double>> topKeywords) {
        Set<String> matched = new HashSet<>();
        for (Map.Entry<String, Double> entry : topKeywords) {
            String kw = entry.getKey();
            for (Map.Entry<String, Set<String>> product : CATALOG_KEYWORDS.entrySet()) {
                if (product.getValue().contains(kw)) {
                    matched.add(product.getKey());
                }
            }
        }
        return matched.size();
    }

    private String formatKeywords(List<Map.Entry<String, Double>> top) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(top.get(i).getKey());
        }
        return sb.toString();
    }
}
