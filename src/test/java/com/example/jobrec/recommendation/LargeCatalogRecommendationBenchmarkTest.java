package com.example.jobrec.recommendation;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Faithful simulation of RecommendationService at production scale:
 * internal catalog (MySQL) first, then external market (SerpAPI) supplement per keyword.
 */
class LargeCatalogRecommendationBenchmarkTest {

    private static final int INTERNAL_CATALOG_SIZE = 1_000;
    private static final int EXTERNAL_MARKET_SIZE = 3_000;
    private static final int USER_COUNT = 500;
    private static final int RESULTS_PER_SERP_QUERY = 20;
    private static final long SEED = 2026L;

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
    private final ProductIndex index = ProductIndex.build(
            INTERNAL_CATALOG_SIZE, EXTERNAL_MARKET_SIZE, SEED);

    @Test
    void benchmark1000Internal3000External() {
        System.out.println("\n=== Production-scale recommendation benchmark ===");
        System.out.printf("Internal catalog: %d | External market pool: %d | Users: %d%n",
                INTERNAL_CATALOG_SIZE, EXTERNAL_MARKET_SIZE, USER_COUNT);
        System.out.printf("SerpAPI returns up to %d results per keyword query%n%n",
                RESULTS_PER_SERP_QUERY);

        System.out.println("Config  AvgInternal  AvgExternal  AvgTotal  P95Total  SerpCalls  Int/Ext ratio");
        for (int[] cfg : List.of(
                new int[]{3, 3}, new int[]{3, 5}, new int[]{3, 10},
                new int[]{5, 3}, new int[]{5, 5}, new int[]{5, 10},
                new int[]{8, 3}, new int[]{8, 5}, new int[]{8, 10},
                new int[]{10, 10}, new int[]{10, 20}, new int[]{15, 10})) {
            RunResult r = simulateAllUsers(cfg[0], cfg[1]);
            System.out.printf("%3d×%-3d  %11.1f  %11.1f  %8.1f  %8.0f  %9d  %5.1f:1%n",
                    cfg[0], cfg[1], r.avgInternal, r.avgExternal, r.avgTotal, r.p95Total,
                    cfg[0] * cfg[1], r.internalToExternalRatio());
        }
        assertTrue(index.internalProducts.size() >= 1_000);
    }

    @Test
    void compareTopK5vs8vs11AndLarger() {
        System.out.println("\n=== Top-K comparison at 1000 catalog (marketCap=3 fixed) ===");
        System.out.println("TopK  AvgInternal  Marginal  AvgExternal  SerpCalls  ScoreCov%");
        int prev = 0;
        for (int k : List.of(3, 5, 8, 11, 15, 20)) {
            RunResult r = simulateAllUsers(k, 3);
            int marginal = prev == 0 ? 0 : (int) Math.round(r.avgInternal - prev);
            double scoreCov = avgScoreCoverage(k);
            System.out.printf("%-4d  %11.1f  %+8d  %11.1f  %9d  %8.1f%n",
                    k, r.avgInternal, marginal, r.avgExternal, k * 3, scoreCov);
            prev = (int) Math.round(r.avgInternal);
        }
    }

    private double avgScoreCoverage(int topK) {
        double sum = 0;
        for (SyntheticUser user : index.users) {
            Map<String, Double> scores = tfidf.computeScores(
                    user.termFrequencies, index.internalDocFreq, index.internalSize);
            if (scores.isEmpty()) continue;
            double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            double covered = tfidf.getTopKeywords(scores, topK).stream()
                    .mapToDouble(Map.Entry::getValue).sum();
            sum += 100.0 * covered / total;
        }
        return sum / index.users.size();
    }

    @Test
    void compareThreshold3AgainstHighVolume() {
        System.out.println("\n=== Why 3 vs returning dozens (1000 internal + 3000 external) ===");
        RunResult baseline = simulateAllUsers(3, 3);
        RunResult moderate = simulateAllUsers(5, 5);
        RunResult high = simulateAllUsers(10, 10);
        RunResult extreme = simulateAllUsers(10, 20);

        printComparison("3×3  (current)", baseline);
        printComparison("5×5", moderate);
        printComparison("10×10 (~100 ext max)", high);
        printComparison("10×20 (~200 ext max)", extreme);

        double extGainHigh = high.avgExternal - baseline.avgExternal;
        double intGainHigh = high.avgInternal - baseline.avgInternal;
        System.out.printf("%n3→10×10: internal +%.0f, external +%.0f, serp calls 9→100 (+%d)%n",
                intGainHigh, extGainHigh, 91);
        System.out.printf("External share at 3×3: %.1f%% | at 10×10: %.1f%%%n",
                100.0 * baseline.avgExternal / baseline.avgTotal,
                100.0 * high.avgExternal / high.avgTotal);
    }

    @Test
    void catalogSizeSensitivity() {
        System.out.println("\n=== Catalog size sensitivity (topK=3, marketCap=3) ===");
        System.out.println("Catalog   AvgInternal  AvgExternal  AvgTotal  SerpCalls");
        for (int size : List.of(100, 500, 1_000, 2_000, 5_000)) {
            ProductIndex sized = ProductIndex.build(size, EXTERNAL_MARKET_SIZE, SEED);
            RunResult r = simulateAllUsers(sized, 3, 3);
            System.out.printf("%-8d  %11.1f  %11.1f  %8.1f  %9d%n",
                    size, r.avgInternal, r.avgExternal, r.avgTotal, 9);
        }
    }

    private void printComparison(String label, RunResult r) {
        System.out.printf("%-22s internal=%.0f  external=%.0f  total=%.0f  (ext %.0f%% of total)%n",
                label, r.avgInternal, r.avgExternal, r.avgTotal,
                100.0 * r.avgExternal / r.avgTotal);
    }

    private RunResult simulateAllUsers(int topK, int marketCapPerKeyword) {
        return simulateAllUsers(index, topK, marketCapPerKeyword);
    }

    private RunResult simulateAllUsers(ProductIndex productIndex, int topK, int marketCapPerKeyword) {
        return simulateAllUsers(productIndex, topK, marketCapPerKeyword, productIndex.users);
    }

    private RunResult simulateAllUsers(ProductIndex productIndex, int topK, int marketCapPerKeyword,
                                       List<SyntheticUser> users) {
        List<Integer> internalCounts = new ArrayList<>();
        List<Integer> externalCounts = new ArrayList<>();
        List<Integer> totalCounts = new ArrayList<>();

        for (SyntheticUser user : users) {
            RecommendationOutcome outcome = simulateRecommend(
                    productIndex, user, topK, marketCapPerKeyword);
            internalCounts.add(outcome.internalCount);
            externalCounts.add(outcome.externalCount);
            totalCounts.add(outcome.internalCount + outcome.externalCount);
        }

        List<Integer> sortedTotal = new ArrayList<>(totalCounts);
        Collections.sort(sortedTotal);
        return new RunResult(
                avg(internalCounts), avg(externalCounts), avg(totalCounts),
                percentile(sortedTotal, 95));
    }

    private double avgScoreCoverage(ProductIndex productIndex, int topK, List<SyntheticUser> users) {
        double sum = 0;
        for (SyntheticUser user : users) {
            Map<String, Double> scores = tfidf.computeScores(
                    user.termFrequencies, productIndex.internalDocFreq, productIndex.internalSize);
            if (scores.isEmpty()) {
                continue;
            }
            double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            double covered = tfidf.getTopKeywords(scores, topK).stream()
                    .mapToDouble(Map.Entry::getValue).sum();
            sum += 100.0 * covered / total;
        }
        return sum / users.size();
    }

    @Test
    void stressTestLargeScale() {
        int[][] scales = {
                {1_000, 3_000, 500},
                {5_000, 15_000, 2_000},
                {10_000, 30_000, 10_000},
                {20_000, 60_000, 20_000},
        };

        System.out.println("\n=== Stress test: scaling users + catalog + market ===");
        for (int[] scale : scales) {
            int internal = scale[0];
            int external = scale[1];
            int users = scale[2];

            long buildStart = System.currentTimeMillis();
            ProductIndex productIndex = ProductIndex.build(internal, external, users, SEED);
            long buildMs = System.currentTimeMillis() - buildStart;

            long simStart = System.currentTimeMillis();
            RunResult k3 = simulateAllUsers(productIndex, 3, 3, productIndex.users);
            RunResult k5 = simulateAllUsers(productIndex, 5, 3, productIndex.users);
            RunResult k8 = simulateAllUsers(productIndex, 8, 3, productIndex.users);
            RunResult k11 = simulateAllUsers(productIndex, 11, 3, productIndex.users);
            long simMs = System.currentTimeMillis() - simStart;

            double cov5 = avgScoreCoverage(productIndex, 5, productIndex.users);
            double cov11 = avgScoreCoverage(productIndex, 11, productIndex.users);

            System.out.printf("%n[%d internal | %d external | %d users | build %dms | sim %dms]%n",
                    internal, external, users, buildMs, simMs);
            System.out.println("TopK  AvgInternal  Marginal(vs prev)  AvgExternal  SerpCalls  ScoreCov%");
            printStressRow(3, k3, 0, avgScoreCoverage(productIndex, 3, productIndex.users));
            printStressRow(5, k5, (int) Math.round(k5.avgInternal - k3.avgInternal), cov5);
            printStressRow(8, k8, (int) Math.round(k8.avgInternal - k5.avgInternal),
                    avgScoreCoverage(productIndex, 8, productIndex.users));
            printStressRow(11, k11, (int) Math.round(k11.avgInternal - k8.avgInternal), cov11);
        }
    }

    private void printStressRow(int k, RunResult r, int marginal, double scoreCov) {
        System.out.printf("%-4d  %11.1f  %+16d  %11.1f  %9d  %8.1f%n",
                k, r.avgInternal, marginal, r.avgExternal, k * 3, scoreCov);
    }

    /** Mirrors RecommendationService.recommendItems() without live DB/SerpAPI. */
    private RecommendationOutcome simulateRecommend(ProductIndex productIndex,
                                                    SyntheticUser user,
                                                    int topK,
                                                    int marketCapPerKeyword) {
        Map<String, Double> scores = tfidf.computeScores(
                user.termFrequencies, productIndex.internalDocFreq, productIndex.internalSize);
        List<String> topKeywords = tfidf.getTopKeywords(scores, topK).stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topKeywords.isEmpty()) {
            return new RecommendationOutcome(0, 0);
        }

        Set<String> visited = new HashSet<>();
        int internal = 0;

        for (String keyword : topKeywords) {
            for (String productId : productIndex.searchInternal(keyword)) {
                if (user.favorites.contains(productId)) {
                    continue;
                }
                if (visited.add(productId)) {
                    internal++;
                }
            }
        }

        int external = 0;
        for (String keyword : topKeywords) {
            int added = 0;
            for (String productId : productIndex.searchExternal(keyword)) {
                if (added >= marketCapPerKeyword) {
                    break;
                }
                if (user.favorites.contains(productId)) {
                    continue;
                }
                if (visited.add(productId)) {
                    external++;
                    added++;
                }
            }
        }
        return new RecommendationOutcome(internal, external);
    }

    private double avg(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private double percentile(List<Integer> sorted, int pct) {
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil(pct / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(0, idx));
    }

    private static class RecommendationOutcome {
        final int internalCount;
        final int externalCount;

        RecommendationOutcome(int internalCount, int externalCount) {
            this.internalCount = internalCount;
            this.externalCount = externalCount;
        }
    }

    private static class RunResult {
        final double avgInternal;
        final double avgExternal;
        final double avgTotal;
        final double p95Total;

        RunResult(double avgInternal, double avgExternal, double avgTotal, double p95Total) {
            this.avgInternal = avgInternal;
            this.avgExternal = avgExternal;
            this.avgTotal = avgTotal;
            this.p95Total = p95Total;
        }

        double internalToExternalRatio() {
            return avgExternal == 0 ? avgInternal : avgInternal / avgExternal;
        }
    }

    private static class SyntheticUser {
        final Set<String> favorites;
        final Map<String, Integer> termFrequencies;

        SyntheticUser(Set<String> favorites, Map<String, Integer> termFrequencies) {
            this.favorites = favorites;
            this.termFrequencies = termFrequencies;
        }
    }

    private static class ProductIndex {
        final int internalSize;
        final Map<String, Set<String>> internalProducts;
        final Map<String, Integer> internalDocFreq;
        final Map<String, List<String>> internalByKeyword;
        final Map<String, List<String>> externalByKeyword;
        final List<SyntheticUser> users;

        private ProductIndex(int internalSize,
                             Map<String, Set<String>> internalProducts,
                             Map<String, Integer> internalDocFreq,
                             Map<String, List<String>> internalByKeyword,
                             Map<String, List<String>> externalByKeyword,
                             List<SyntheticUser> users) {
            this.internalSize = internalSize;
            this.internalProducts = internalProducts;
            this.internalDocFreq = internalDocFreq;
            this.internalByKeyword = internalByKeyword;
            this.externalByKeyword = externalByKeyword;
            this.users = users;
        }

        static ProductIndex build(int internalSize, int externalSize, int userCount, long seed) {
            Random rng = new Random(seed);
            Map<String, Set<String>> internal = new HashMap<>(internalSize * 2);
            Map<String, Integer> docFreq = new HashMap<>();
            Map<String, List<String>> internalByKeyword = new HashMap<>();

            for (int i = 0; i < internalSize; i++) {
                Set<String> kws = pickKeywords(rng, 4);
                String id = "in-" + i;
                internal.put(id, kws);
                for (String kw : kws) {
                    docFreq.merge(kw, 1, Integer::sum);
                    internalByKeyword.computeIfAbsent(kw, k -> new ArrayList<>()).add(id);
                }
            }

            Map<String, List<String>> externalByKeyword = new HashMap<>();
            for (int i = 0; i < externalSize; i++) {
                Set<String> kws = pickKeywords(rng, 3);
                String id = "ext-" + i;
                for (String kw : kws) {
                    externalByKeyword.computeIfAbsent(kw, k -> new ArrayList<>()).add(id);
                }
            }
            for (List<String> ids : externalByKeyword.values()) {
                ids.sort(String::compareTo);
            }

            List<SyntheticUser> users = new ArrayList<>(userCount);
            for (int u = 0; u < userCount; u++) {
                int favCount = 1 + rng.nextInt(8);
                Set<String> favorites = new HashSet<>();
                while (favorites.size() < favCount) {
                    favorites.add("in-" + rng.nextInt(internalSize));
                }
                Map<String, Integer> termFreq = new HashMap<>();
                for (String fav : favorites) {
                    for (String kw : internal.get(fav)) {
                        termFreq.merge(kw, 1, Integer::sum);
                    }
                }
                users.add(new SyntheticUser(favorites, termFreq));
            }
            return new ProductIndex(internalSize, internal, docFreq, internalByKeyword,
                    externalByKeyword, users);
        }

        static ProductIndex build(int internalSize, int externalSize, long seed) {
            return build(internalSize, externalSize, USER_COUNT, seed);
        }

        List<String> searchInternal(String keyword) {
            return internalByKeyword.getOrDefault(keyword, Collections.emptyList());
        }

        List<String> searchExternal(String keyword) {
            List<String> pool = externalByKeyword.getOrDefault(keyword, Collections.emptyList());
            return pool.subList(0, Math.min(RESULTS_PER_SERP_QUERY, pool.size()));
        }

        private static Set<String> pickKeywords(Random rng, int count) {
            Set<String> picked = new LinkedHashSet<>();
            while (picked.size() < count) {
                picked.add(VOCAB[rng.nextInt(VOCAB.length)]);
            }
            return picked;
        }
    }
}
