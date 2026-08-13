package com.example.jobrec.recommendation;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Large-scale synthetic benchmark for recommendation threshold tuning.
 * Simulates thousands of catalog items and hundreds of user profiles without live SerpAPI.
 */
class RecommendationThresholdBenchmarkTest {

    private static final int CATALOG_SIZE = 2_000;
    private static final int USER_COUNT = 500;
    private static final int KEYWORDS_PER_PRODUCT = 4;
    private static final long SEED = 42L;

    private static final String[] VOCAB = {
            "crm", "sales", "customer", "saas", "analytics", "dashboard", "bi", "data",
            "ai", "nlp", "ml", "recommendation", "cloud", "migration", "aws", "enterprise",
            "security", "compliance", "software", "api", "integration", "automation",
            "marketing", "support", "billing", "devops", "monitoring", "workflow",
            "collaboration", "storage", "database", "mobile", "web", "ecommerce",
            "finance", "hr", "legal", "healthcare", "retail", "logistics", "iot",
            "blockchain", "kubernetes", "docker", "terraform", "python", "java", "react"
    };

    private final TFIDF tfidf = new TFIDF();
    private final SyntheticCatalog catalog = SyntheticCatalog.generate(CATALOG_SIZE, SEED);

    @Test
    void largeScaleTopKComparison() {
        System.out.println("\n=== Large-scale Top-K benchmark ===");
        System.out.printf("Catalog: %d products | Users: %d | Avg favorites/user: %.1f%n",
                CATALOG_SIZE, USER_COUNT, catalog.avgFavoritesPerUser());

        int marketCap = 3;
        System.out.printf("%nTop-K sweep (market cap = %d per keyword):%n", marketCap);
        printHeader("K", "AvgCatalog", "P50Catalog", "P95Catalog", "AvgScore%", "MarginalCatΔ", "MaxSerp/user");

        int prevAvgCatalog = -1;
        for (int k : List.of(3, 5, 8, 10, 15, 20, 30)) {
            AggregateStats stats = runTopKSweep(k, marketCap);
            int marginal = prevAvgCatalog < 0 ? 0 : stats.avgCatalogHits - prevAvgCatalog;
            prevAvgCatalog = stats.avgCatalogHits;
            System.out.printf("%-4d %-11.1f %-11.1f %-11.1f %-11.1f %-13d %-13d%n",
                    k, (double) stats.avgCatalogHits, stats.p50CatalogHits, stats.p95CatalogHits,
                    stats.avgScoreCoveragePct, marginal, k * marketCap);
        }
        assertTrue(catalog.productKeywords.size() == CATALOG_SIZE);
    }

    @Test
    void largeScaleMarketCapComparison() {
        System.out.println("\n=== Market cap sweep (topK = 3) ===");
        printHeader("Cap", "AvgTotal", "AvgCatalog", "AvgMarket", "MaxTotal/user", "SerpCalls/user");

        for (int cap : List.of(3, 5, 8, 10, 15, 20, 30, 50)) {
            AggregateStats stats = runTopKSweep(3, cap);
            double avgMarket = Math.min(cap * 3.0, stats.avgMarketSlotsUsed);
            System.out.printf("%-4d %-11.1f %-11.1f %-11.1f %-13d %-13d%n",
                    cap, stats.avgCatalogHits + avgMarket, (double) stats.avgCatalogHits, avgMarket,
                    stats.maxTotalResults, 3 * cap);
        }
    }

    @Test
    void highVolumeReturnComparison() {
        System.out.println("\n=== High-volume return configs (catalog + market upper bound) ===");
        System.out.printf("%-18s %-12s %-12s %-14s %-12s%n",
                "Config(topK×cap)", "AvgCatalog", "MaxMarket", "MaxTotal", "AvgScore%");

        List<int[]> configs = List.of(
                new int[]{3, 3},    // current production
                new int[]{5, 5},
                new int[]{8, 8},
                new int[]{10, 10},  // ~100 market items
                new int[]{10, 20},  // ~200 market items
                new int[]{15, 20},  // ~300 market items
                new int[]{20, 30}   // ~600 market items
        );

        for (int[] cfg : configs) {
            int topK = cfg[0];
            int cap = cfg[1];
            AggregateStats stats = runTopKSweep(topK, cap);
            int maxMarket = topK * cap;
            System.out.printf("%-18s %-12.1f %-12d %-14d %-12.1f%n",
                    topK + "×" + cap, (double) stats.avgCatalogHits, maxMarket,
                    stats.avgCatalogHits + maxMarket, stats.avgScoreCoveragePct);
        }
    }

    @Test
    void diminishingReturnsAnalysis() {
        System.out.println("\n=== Diminishing returns: catalog hit gain per extra SerpAPI call ===");
        System.out.printf("%-20s %-14s %-16s %-18s%n",
                "Config", "AvgCatalog", "SerpCalls/user", "Catalog/SerpCall");

        int baselineCatalog = runTopKSweep(3, 3).avgCatalogHits;
        for (int[] cfg : List.of(new int[]{3, 3}, new int[]{5, 5}, new int[]{10, 10},
                new int[]{10, 20}, new int[]{20, 30})) {
            AggregateStats stats = runTopKSweep(cfg[0], cfg[1]);
            int serpCalls = cfg[0] * cfg[1];
            double catalogGain = stats.avgCatalogHits - baselineCatalog;
            double efficiency = serpCalls == 0 ? 0 : catalogGain / serpCalls;
            System.out.printf("%-20s %-14.1f %-16d %-18.4f%n",
                    cfg[0] + "×" + cfg[1], (double) stats.avgCatalogHits, serpCalls, efficiency);
        }
    }

    private AggregateStats runTopKSweep(int topK, int marketCapPerKeyword) {
        List<Integer> catalogHits = new ArrayList<>();
        List<Double> scoreCoverage = new ArrayList<>();
        List<Integer> totalResults = new ArrayList<>();
        List<Double> marketSlots = new ArrayList<>();

        for (SyntheticUser user : catalog.users) {
            Map<String, Double> scores = tfidf.computeScores(
                    user.termFrequencies, catalog.documentFrequencies, CATALOG_SIZE);
            if (scores.isEmpty()) {
                continue;
            }
            double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            List<Map.Entry<String, Double>> top = tfidf.getTopKeywords(scores, topK);
            double covered = top.stream().mapToDouble(Map.Entry::getValue).sum();

            Set<String> matched = catalog.matchProducts(top.stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()));
            int hits = matched.size();
            int marketUsed = Math.min(marketCapPerKeyword * top.size(),
                    simulateMarketMatches(top, matched, marketCapPerKeyword));

            catalogHits.add(hits);
            scoreCoverage.add(100.0 * covered / totalScore);
            totalResults.add(hits + marketUsed);
            marketSlots.add((double) marketUsed);
        }

        Collections.sort(catalogHits);
        return new AggregateStats(
                (int) Math.round(average(catalogHits.stream().map(Integer::doubleValue).collect(Collectors.toList()))),
                percentile(catalogHits, 50),
                percentile(catalogHits, 95),
                average(scoreCoverage),
                (int) Math.round(average(totalResults.stream().map(Integer::doubleValue).collect(Collectors.toList()))),
                average(marketSlots),
                catalogHits.isEmpty() ? 0 : catalogHits.get(catalogHits.size() - 1)
        );
    }

    /** Simulate market items: assume ~60% of slots fill, dedup against catalog hits. */
    private int simulateMarketMatches(List<Map.Entry<String, Double>> topKeywords,
                                      Set<String> catalogMatched, int capPerKeyword) {
        Set<String> marketIds = new HashSet<>();
        Random rng = new Random(SEED + topKeywords.hashCode());
        for (Map.Entry<String, Double> kw : topKeywords) {
            int added = 0;
            while (added < capPerKeyword) {
                if (rng.nextDouble() > 0.6) {
                    break;
                }
                String fakeId = "market-" + kw.getKey() + "-" + added;
                if (!catalogMatched.contains(fakeId)) {
                    marketIds.add(fakeId);
                }
                added++;
            }
        }
        return marketIds.size();
    }

    private void printHeader(String... cols) {
        System.out.printf("%-4s", cols[0]);
        for (int i = 1; i < cols.length; i++) {
            System.out.printf(" %-12s", cols[i]);
        }
        System.out.println();
    }

    private double average(List<? extends Number> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Number::doubleValue).average().orElse(0);
    }

    private double percentile(List<Integer> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil(pct / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(0, idx));
    }

    private static class AggregateStats {
        final int avgCatalogHits;
        final double p50CatalogHits;
        final double p95CatalogHits;
        final double avgScoreCoveragePct;
        final int maxTotalResults;
        final double avgMarketSlotsUsed;

        AggregateStats(int avgCatalogHits, double p50, double p95, double avgScoreCoveragePct,
                       int maxTotalResults, double avgMarketSlotsUsed, int ignored) {
            this.avgCatalogHits = avgCatalogHits;
            this.p50CatalogHits = p50;
            this.p95CatalogHits = p95;
            this.avgScoreCoveragePct = avgScoreCoveragePct;
            this.maxTotalResults = maxTotalResults;
            this.avgMarketSlotsUsed = avgMarketSlotsUsed;
        }
    }

    private static class SyntheticCatalog {
        final Map<String, Set<String>> productKeywords;
        final Map<String, Integer> documentFrequencies;
        final List<SyntheticUser> users;

        private SyntheticCatalog(Map<String, Set<String>> productKeywords,
                                 Map<String, Integer> documentFrequencies,
                                 List<SyntheticUser> users) {
            this.productKeywords = productKeywords;
            this.documentFrequencies = documentFrequencies;
            this.users = users;
        }

        static SyntheticCatalog generate(int catalogSize, long seed) {
            Random rng = new Random(seed);
            Map<String, Set<String>> products = new LinkedHashMap<>();
            Map<String, Integer> docFreq = new HashMap<>();

            for (int i = 0; i < catalogSize; i++) {
                Set<String> keywords = pickKeywords(rng, KEYWORDS_PER_PRODUCT);
                products.put("product-" + i, keywords);
                for (String kw : keywords) {
                    docFreq.merge(kw, 1, Integer::sum);
                }
            }

            List<SyntheticUser> users = new ArrayList<>();
            for (int u = 0; u < USER_COUNT; u++) {
                int favCount = 1 + rng.nextInt(8);
                Set<String> favorites = new HashSet<>();
                while (favorites.size() < favCount) {
                    favorites.add("product-" + rng.nextInt(catalogSize));
                }
                Map<String, Integer> termFreq = new HashMap<>();
                for (String fav : favorites) {
                    for (String kw : products.get(fav)) {
                        termFreq.merge(kw, 1, Integer::sum);
                    }
                }
                users.add(new SyntheticUser("user-" + u, termFreq));
            }
            return new SyntheticCatalog(products, docFreq, users);
        }

        Set<String> matchProducts(List<String> keywords) {
            Set<String> matched = new HashSet<>();
            for (Map.Entry<String, Set<String>> entry : productKeywords.entrySet()) {
                for (String kw : keywords) {
                    if (entry.getValue().contains(kw)) {
                        matched.add(entry.getKey());
                        break;
                    }
                }
            }
            return matched;
        }

        double avgFavoritesPerUser() {
            return users.stream()
                    .mapToInt(u -> u.termFrequencies.values().stream().mapToInt(Integer::intValue).sum())
                    .average().orElse(0) / KEYWORDS_PER_PRODUCT;
        }

        private static Set<String> pickKeywords(Random rng, int count) {
            Set<String> picked = new HashSet<>();
            while (picked.size() < count) {
                picked.add(VOCAB[rng.nextInt(VOCAB.length)]);
            }
            return picked;
        }
    }

    private static class SyntheticUser {
        final Map<String, Integer> termFrequencies;

        SyntheticUser(String id, Map<String, Integer> termFrequencies) {
            this.termFrequencies = termFrequencies;
        }
    }
}
