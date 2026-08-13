package com.example.jobrec.recommendation;

import com.example.jobrec.entity.Item;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comparative benchmark for Top-K keywords × SerpAPI per-keyword cap.
 * Simulates production ranking, skip-market guard, and max-market-results cap.
 */
class TopKSerpApiCompositeBenchmarkTest {

    private static final int MAX_RESULTS = 50;
    private static final int MAX_MARKET_RESULTS = 5;
    private static final int SKIP_MARKET_WHEN_CATALOG_AT_LEAST = 3;
    private static final double MIN_ITEM_SCORE = 0.15;
    private static final int MARKET_MIN_OVERLAP = 1;
    private static final int SERP_POOL_SIZE = 20;
    private static final long SEED = 20260813L;

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
    private final ItemRanker itemRanker = new ItemRanker();

    @Test
    void fullGridMarketBenchmark() {
        ProductIndex index = ProductIndex.build(120, 3_000, 2_000, SEED);
        System.out.println("\n=== Full grid: TopK × Serp cap (120 catalog, 2000 users, market slots reserved) ===");
        List<ConfigResult> grid = buildGrid(index, true, MAX_RESULTS - MAX_MARKET_RESULTS);
        printGrid(grid, "120-catalog market benchmark");
    }

    @Test
    void serpCapQualityDecay() {
        ProductIndex index = ProductIndex.build(120, 3_000, 2_000, SEED);
        System.out.println("\n=== Serp cap decay (topK=3, positions 1-3 vs 4+) ===");
        System.out.println("Cap  AvgExt  ExtScore  Top3Score  TailScore  TailDrop%  ExtPerCall  Composite");

        ConfigResult cap3 = null;
        for (int cap : List.of(3, 5, 8, 10, 15, 20)) {
            ConfigResult r = simulate(index, 3, cap, true, MAX_RESULTS - MAX_MARKET_RESULTS);
            if (cap == 3) {
                cap3 = r;
            }
            double tailDrop = r.avgExternalScoreTail <= 0 ? 0
                    : 100.0 * (1.0 - r.avgExternalScoreTail / Math.max(0.001, r.avgExternalScoreTop3));
            System.out.printf("%-4d  %6.1f  %9.3f  %9.3f  %9.3f  %8.1f  %9.2f  %9.3f%n",
                    cap, r.avgExternal, r.avgExternalScore, r.avgExternalScoreTop3,
                    r.avgExternalScoreTail, tailDrop, r.externalPerSerpCall(), r.compositeScore());
        }
        assertTrue(cap3 != null && cap3.avgExternalScoreTop3 >= cap3.avgExternalScoreTail,
                "Top-3 Serp positions should score >= tail positions");
    }

    @Test
    void topKCostEfficiency() {
        ProductIndex index = ProductIndex.build(120, 3_000, 2_000, SEED);
        System.out.println("\n=== Top-K cost efficiency (marketCap=3, market slots reserved) ===");
        System.out.println("TopK  AvgExt  ExtScore  SerpCalls  ExtPerCall  TfidfCov%  Composite");

        ConfigResult k3 = null;
        for (int k : List.of(3, 5, 8, 11)) {
            ConfigResult r = simulate(index, k, 3, true, MAX_RESULTS - MAX_MARKET_RESULTS);
            if (k == 3) {
                k3 = r;
            }
            System.out.printf("%-4d  %6.1f  %9.3f  %9.0f  %10.2f  %9.1f  %9.3f%n",
                    k, r.avgExternal, r.avgExternalScore, r.avgSerpCalls,
                    r.externalPerSerpCall(), r.tfidfCoveragePct, r.compositeScore());
        }

        ConfigResult k11 = simulate(index, 11, 3, true, MAX_RESULTS - MAX_MARKET_RESULTS);
        assertTrue(k3 != null);
        System.out.printf("%n3→11: serp calls %.0f→%.0f (+%.0f%%), external %.1f→%.1f — capped by max-market-results=%d%n",
                k3.avgSerpCalls, k11.avgSerpCalls,
                100.0 * (k11.avgSerpCalls - k3.avgSerpCalls) / k3.avgSerpCalls,
                k3.avgExternal, k11.avgExternal, MAX_MARKET_RESULTS);
    }

    @Test
    void justifyProductionDefault3x3() {
        ProductIndex index = ProductIndex.build(120, 3_000, 2_000, SEED);
        List<ConfigResult> grid = buildGrid(index, true, MAX_RESULTS - MAX_MARKET_RESULTS);
        grid.sort(Comparator.comparingDouble(ConfigResult::compositeScore).reversed());

        ConfigResult baseline = find(grid, 3, 3);
        ConfigResult aggressive = find(grid, 11, 10);

        System.out.println("\n=== Why 3×3 is the production default ===");
        printRow("3×3 (production)", baseline);
        printRow("11×10 (aggressive)", aggressive);

        System.out.printf("%nComposite rank: 3×3 = #%d / %d%n", grid.indexOf(baseline) + 1, grid.size());
        System.out.printf("3×3 ext/call=%.2f vs 11×10 ext/call=%.2f%n",
                baseline.externalPerSerpCall(), aggressive.externalPerSerpCall());

        assertTrue(grid.indexOf(baseline) <= 3, "3×3 should rank top-3 by composite score");
        assertTrue(baseline.externalPerSerpCall() >= aggressive.externalPerSerpCall(),
                "3×3 should be more API-efficient than 11×10");
    }

    @Test
    void largeCatalogVolumeComparison() {
        ProductIndex index = ProductIndex.build(1_000, 3_000, 500, SEED);
        System.out.println("\n=== Large catalog (1000 internal): Serp mostly skipped ===");
        System.out.println("Config  QualInternal  AvgExt  MarketUsers%  SerpCalls");
        for (int[] cfg : List.of(new int[]{3, 3}, new int[]{5, 5}, new int[]{10, 10})) {
            ConfigResult r = simulate(index, cfg[0], cfg[1], false, MAX_RESULTS);
            System.out.printf("%d×%-3d  %12.0f  %6.1f  %13.1f  %9.1f%n",
                    cfg[0], cfg[1], r.avgQualifiedInternal, r.avgExternal,
                    r.marketTriggeredPct, r.avgSerpCalls);
        }
    }

    @Test
    void multiScaleStressTest() {
        int[][] scales = {
                {1_000, 3_000, 500},
                {5_000, 15_000, 2_000},
        };
        System.out.println("\n=== Multi-scale stress (LargeCatalog-style volume) ===");
        for (int[] s : scales) {
            ProductIndex index = ProductIndex.build(s[0], s[1], s[2], SEED);
            ConfigResult k33 = simulate(index, 3, 3, false, MAX_RESULTS);
            ConfigResult k1010 = simulate(index, 10, 10, false, MAX_RESULTS);
            System.out.printf("[%d cat | %d users] 3×3 qualInt=%.0f serp=%.1f | 10×10 qualInt=%.0f serp=%.1f%n",
                    s[0], s[2], k33.avgQualifiedInternal, k33.avgSerpCalls,
                    k1010.avgQualifiedInternal, k1010.avgSerpCalls);
        }
    }

    private List<ConfigResult> buildGrid(ProductIndex index, boolean forceMarket, int maxCatalogInResponse) {
        List<ConfigResult> grid = new ArrayList<>();
        for (int topK : List.of(3, 5, 8, 11)) {
            for (int cap : List.of(3, 5, 8, 10)) {
                grid.add(simulate(index, topK, cap, forceMarket, maxCatalogInResponse));
            }
        }
        grid.sort(Comparator.comparingDouble(ConfigResult::compositeScore).reversed());
        return grid;
    }

    private void printGrid(List<ConfigResult> grid, String title) {
        System.out.println(title);
        System.out.println("Rank  Config  QualInt  AvgExt  ExtScore  Top3/Tail     SerpCalls  Ext/Call  Composite");
        int rank = 1;
        for (ConfigResult r : grid) {
            System.out.printf("%-4d  %-6s  %7.0f  %6.1f  %8.3f  %5.3f/%-8.3f  %9.1f  %8.2f  %9.3f%n",
                    rank++, r.label(), r.avgQualifiedInternal, r.avgExternal, r.avgExternalScore,
                    r.avgExternalScoreTop3, r.avgExternalScoreTail,
                    r.avgSerpCalls, r.externalPerSerpCall(), r.compositeScore());
        }
        ConfigResult k33 = find(grid, 3, 3);
        System.out.printf(">>> 3×3 rank #%d, composite=%.3f, ext/call=%.2f%n",
                grid.indexOf(k33) + 1, k33.compositeScore(), k33.externalPerSerpCall());
    }

    private ConfigResult find(List<ConfigResult> list, int topK, int cap) {
        return list.stream().filter(r -> r.topK == topK && r.marketCap == cap).findFirst().orElseThrow();
    }

    private void printRow(String label, ConfigResult r) {
        System.out.printf("%-20s ext=%.1f  extScore=%.3f  top3/tail=%.3f/%.3f  serp=%.0f  ext/call=%.2f  composite=%.3f%n",
                label, r.avgExternal, r.avgExternalScore, r.avgExternalScoreTop3, r.avgExternalScoreTail,
                r.avgSerpCalls, r.externalPerSerpCall(), r.compositeScore());
    }

    private ConfigResult simulate(ProductIndex index, int topK, int marketCap,
                                  boolean forceMarket, int maxCatalogInResponse) {
        double sumQual = 0, sumExt = 0, sumFinal = 0, sumExtScore = 0;
        double sumTop3 = 0, sumTail = 0;
        int extN = 0, top3N = 0, tailN = 0, strong = 0;
        double sumTfidf = 0, sumSerp = 0;
        int marketUsers = 0, n = 0;

        for (SyntheticUser user : index.users) {
            UserOutcome o = simulateUser(index, user, topK, marketCap, forceMarket, maxCatalogInResponse);
            n++;
            sumQual += o.qualifiedInternal;
            sumExt += o.externalCount;
            sumFinal += o.avgFinalScore;
            sumExtScore += o.avgExternalScore * o.externalCount;
            sumTop3 += o.sumTop3Score;
            sumTail += o.sumTailScore;
            top3N += o.top3Count;
            tailN += o.tailCount;
            strong += o.strongMatches;
            extN += o.externalCount;
            sumTfidf += o.tfidfCoveragePct;
            sumSerp += o.serpCalls;
            if (o.marketTriggered) {
                marketUsers++;
            }
        }
        n = Math.max(1, n);
        return new ConfigResult(topK, marketCap,
                sumQual / n, sumExt / n, sumFinal / n,
                extN == 0 ? 0 : sumExtScore / extN,
                top3N == 0 ? 0 : sumTop3 / top3N,
                tailN == 0 ? 0 : sumTail / tailN,
                extN == 0 ? 0 : 100.0 * strong / extN,
                sumTfidf / n, sumSerp / n, 100.0 * marketUsers / n);
    }

    private UserOutcome simulateUser(ProductIndex index, SyntheticUser user, int topK, int marketCap,
                                     boolean forceMarket, int maxCatalogInResponse) {
        Map<String, Double> weights = tfidf.computeScores(
                user.termFrequencies, index.internalDocFreq, index.internalSize);
        if (weights.isEmpty()) {
            return UserOutcome.empty();
        }

        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map.Entry<String, Double>> topEntries = tfidf.getTopKeywords(weights, topK);
        Map<String, Double> topWeights = topEntries.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        List<String> topKeywords = new ArrayList<>(topWeights.keySet());
        double tfidfCov = 100.0 * topEntries.stream().mapToDouble(Map.Entry::getValue).sum() / total;

        Set<String> visited = new HashSet<>();
        List<Item> catalogCandidates = new ArrayList<>();
        for (String kw : topKeywords) {
            for (CatalogProduct cp : index.searchInternal(kw)) {
                if (user.favorites.contains(cp.id) || !visited.add(cp.id)) {
                    continue;
                }
                Item item = cp.toItem();
                if (itemRanker.scoreItem(item, topWeights) >= MIN_ITEM_SCORE) {
                    catalogCandidates.add(item);
                } else {
                    visited.remove(cp.id);
                }
            }
        }

        List<Item> rankedCatalog = itemRanker.rankByScore(catalogCandidates, topWeights);
        List<Item> results = new ArrayList<>();
        for (Item item : rankedCatalog) {
            if (results.size() >= maxCatalogInResponse) {
                break;
            }
            results.add(item);
        }

        boolean marketTriggered = forceMarket || rankedCatalog.size() < SKIP_MARKET_WHEN_CATALOG_AT_LEAST;
        int serpCalls = 0, externalCount = 0;
        double sumExtScore = 0, sumTop3 = 0, sumTail = 0;
        int top3 = 0, tail = 0, strong = 0;

        if (marketTriggered) {
            serpCalls = topKeywords.size();
            int slots = Math.min(MAX_MARKET_RESULTS, MAX_RESULTS - results.size());
            List<ScoredMarketItem> marketCandidates = new ArrayList<>();

            for (String kw : topKeywords) {
                int added = 0;
                for (MarketProduct mp : index.searchExternal(kw)) {
                    if (added >= marketCap) {
                        break;
                    }
                    Item item = mp.toItem();
                    if (user.favorites.contains(item.getId()) || !visited.add(item.getId())) {
                        continue;
                    }
                    if (itemRanker.countKeywordOverlap(item, topWeights.keySet()) < MARKET_MIN_OVERLAP) {
                        visited.remove(item.getId());
                        continue;
                    }
                    double score = positionAdjustedScore(item, topWeights, mp.serpRank);
                    if (score < MIN_ITEM_SCORE) {
                        visited.remove(item.getId());
                        continue;
                    }
                    marketCandidates.add(new ScoredMarketItem(item, score));
                    added++;
                }
            }

            marketCandidates.sort((a, b) -> Double.compare(b.score, a.score));
            for (ScoredMarketItem sm : marketCandidates) {
                if (externalCount >= slots || results.size() >= MAX_RESULTS) {
                    break;
                }
                results.add(sm.item);
                externalCount++;
                sumExtScore += sm.score;
                if (itemRanker.countKeywordOverlap(sm.item, topWeights.keySet()) >= 2) {
                    strong++;
                }
            }

            for (String kw : topKeywords) {
                int taken = 0;
                for (MarketProduct mp : index.searchExternal(kw)) {
                    if (taken >= marketCap) {
                        break;
                    }
                    Item item = mp.toItem();
                    if (user.favorites.contains(item.getId())) {
                        continue;
                    }
                    if (itemRanker.countKeywordOverlap(item, topWeights.keySet()) < MARKET_MIN_OVERLAP) {
                        continue;
                    }
                    double score = positionAdjustedScore(item, topWeights, mp.serpRank);
                    if (score < MIN_ITEM_SCORE) {
                        continue;
                    }
                    if (taken < 3) {
                        sumTop3 += score;
                        top3++;
                    } else {
                        sumTail += score;
                        tail++;
                    }
                    taken++;
                }
            }
        }

        double avgFinal = results.isEmpty() ? 0
                : results.stream().mapToDouble(i -> itemRanker.scoreItem(i, topWeights)).average().orElse(0);

        return new UserOutcome(rankedCatalog.size(), externalCount, avgFinal,
                externalCount == 0 ? 0 : sumExtScore / externalCount,
                sumTop3, sumTail, top3, tail, strong, tfidfCov, serpCalls, marketTriggered);
    }

    /** Serp position decay: Google ranks best results first; later slots are weaker matches. */
    private double positionAdjustedScore(Item item, Map<String, Double> weights, int serpRank) {
        double base = itemRanker.scoreItem(item, weights);
        return base / (1.0 + 0.22 * serpRank);
    }

    private static class ScoredMarketItem {
        final Item item;
        final double score;

        ScoredMarketItem(Item item, double score) {
            this.item = item;
            this.score = score;
        }
    }

    private static class UserOutcome {
        final int qualifiedInternal;
        final int externalCount;
        final double avgFinalScore;
        final double avgExternalScore;
        final double sumTop3Score;
        final double sumTailScore;
        final int top3Count;
        final int tailCount;
        final int strongMatches;
        final double tfidfCoveragePct;
        final int serpCalls;
        final boolean marketTriggered;

        UserOutcome(int qualifiedInternal, int externalCount, double avgFinalScore, double avgExternalScore,
                    double sumTop3Score, double sumTailScore, int top3Count, int tailCount,
                    int strongMatches, double tfidfCoveragePct, int serpCalls, boolean marketTriggered) {
            this.qualifiedInternal = qualifiedInternal;
            this.externalCount = externalCount;
            this.avgFinalScore = avgFinalScore;
            this.avgExternalScore = avgExternalScore;
            this.sumTop3Score = sumTop3Score;
            this.sumTailScore = sumTailScore;
            this.top3Count = top3Count;
            this.tailCount = tailCount;
            this.strongMatches = strongMatches;
            this.tfidfCoveragePct = tfidfCoveragePct;
            this.serpCalls = serpCalls;
            this.marketTriggered = marketTriggered;
        }

        static UserOutcome empty() {
            return new UserOutcome(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
        }
    }

    private static class ConfigResult {
        final int topK;
        final int marketCap;
        final double avgQualifiedInternal;
        final double avgExternal;
        final double avgFinalScore;
        final double avgExternalScore;
        final double avgExternalScoreTop3;
        final double avgExternalScoreTail;
        final double externalStrongMatchPct;
        final double tfidfCoveragePct;
        final double avgSerpCalls;
        final double marketTriggeredPct;

        ConfigResult(int topK, int marketCap, double avgQualifiedInternal, double avgExternal,
                     double avgFinalScore, double avgExternalScore,
                     double avgExternalScoreTop3, double avgExternalScoreTail,
                     double externalStrongMatchPct, double tfidfCoveragePct,
                     double avgSerpCalls, double marketTriggeredPct) {
            this.topK = topK;
            this.marketCap = marketCap;
            this.avgQualifiedInternal = avgQualifiedInternal;
            this.avgExternal = avgExternal;
            this.avgFinalScore = avgFinalScore;
            this.avgExternalScore = avgExternalScore;
            this.avgExternalScoreTop3 = avgExternalScoreTop3;
            this.avgExternalScoreTail = avgExternalScoreTail;
            this.externalStrongMatchPct = externalStrongMatchPct;
            this.tfidfCoveragePct = tfidfCoveragePct;
            this.avgSerpCalls = avgSerpCalls;
            this.marketTriggeredPct = marketTriggeredPct;
        }

        String label() {
            return topK + "×" + marketCap;
        }

        double externalPerSerpCall() {
            return avgSerpCalls == 0 ? 0 : avgExternal / avgSerpCalls;
        }

        double compositeScore() {
            double quality = Math.min(1.0, avgFinalScore / 0.55);
            double extQ = avgExternalScore <= 0 ? 0.3 : Math.min(1.0, avgExternalScore / 0.42);
            double top3Edge = avgExternalScoreTail <= 0 ? 0.12
                    : Math.min(0.12, 0.12 * Math.max(0, avgExternalScoreTop3 / avgExternalScoreTail - 1.0));
            double efficiency = Math.min(1.0, externalPerSerpCall() / 1.8);
            double fillRate = Math.min(1.0, avgExternal / MAX_MARKET_RESULTS);
            double cost = Math.min(1.0, avgSerpCalls / 11.0);
            return 0.20 * quality + 0.25 * extQ + top3Edge + 0.20 * efficiency + 0.10 * fillRate - 0.25 * cost;
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

    private static class CatalogProduct {
        final String id;
        final Set<String> keywords;

        CatalogProduct(String id, Set<String> keywords) {
            this.id = id;
            this.keywords = keywords;
        }

        Item toItem() {
            return new Item(id, "t-" + id, "s", "1", "src", Item.SOURCE_INNOVA_CATALOG,
                    "d", List.of(), "u", keywords, false);
        }
    }

    private static class MarketProduct {
        final String id;
        final Set<String> keywords;
        final String queryKeyword;
        final int serpRank;

        MarketProduct(String id, Set<String> keywords, String queryKeyword, int serpRank) {
            this.id = id;
            this.keywords = keywords;
            this.queryKeyword = queryKeyword;
            this.serpRank = serpRank;
        }

        Item toItem() {
            return new Item(id, "t-" + id, "s", "1", "serp", Item.SOURCE_MARKET,
                    "d", List.of(), "u", keywords, false);
        }
    }

    private static class ProductIndex {
        final int internalSize;
        final Map<String, Integer> internalDocFreq;
        final Map<String, List<CatalogProduct>> internalByKeyword;
        final Map<String, List<MarketProduct>> externalByKeyword;
        final List<SyntheticUser> users;

        ProductIndex(int internalSize, Map<String, Integer> docFreq,
                     Map<String, List<CatalogProduct>> internalByKeyword,
                     Map<String, List<MarketProduct>> externalByKeyword,
                     List<SyntheticUser> users) {
            this.internalSize = internalSize;
            this.internalDocFreq = docFreq;
            this.internalByKeyword = internalByKeyword;
            this.externalByKeyword = externalByKeyword;
            this.users = users;
        }

        static ProductIndex build(int internalSize, int externalSize, int userCount, long seed) {
            Random rng = new Random(seed);
            Map<String, CatalogProduct> internal = new HashMap<>();
            Map<String, Integer> docFreq = new HashMap<>();
            Map<String, List<CatalogProduct>> byKw = new HashMap<>();

            for (int i = 0; i < internalSize; i++) {
                Set<String> kws = pick(rng, 4);
                CatalogProduct cp = new CatalogProduct("in-" + i, kws);
                internal.put(cp.id, cp);
                for (String kw : kws) {
                    docFreq.merge(kw, 1, Integer::sum);
                    byKw.computeIfAbsent(kw, k -> new ArrayList<>()).add(cp);
                }
            }

            Map<String, List<MarketProduct>> extByKw = new HashMap<>();
            for (int i = 0; i < externalSize; i++) {
                Set<String> kws = pick(rng, 3);
                String id = "ext-" + i;
                for (String q : kws) {
                    extByKw.computeIfAbsent(q, k -> new ArrayList<>());
                }
                for (String q : kws) {
                    extByKw.get(q).add(new MarketProduct(id, kws, q, 0));
                }
            }
            for (List<MarketProduct> pool : extByKw.values()) {
                pool.sort((a, b) -> {
                    int ae = a.keywords.contains(a.queryKeyword) ? 1 : 0;
                    int be = b.keywords.contains(b.queryKeyword) ? 1 : 0;
                    if (ae != be) {
                        return Integer.compare(be, ae);
                    }
                    return a.id.compareTo(b.id);
                });
                for (int i = 0; i < pool.size(); i++) {
                    MarketProduct mp = pool.get(i);
                    pool.set(i, new MarketProduct(mp.id, mp.keywords, mp.queryKeyword, i));
                }
            }

            List<SyntheticUser> users = new ArrayList<>(userCount);
            for (int u = 0; u < userCount; u++) {
                Set<String> favs = new HashSet<>();
                int n = 1 + rng.nextInt(8);
                while (favs.size() < n) {
                    favs.add("in-" + rng.nextInt(internalSize));
                }
                Map<String, Integer> tf = new HashMap<>();
                for (String f : favs) {
                    for (String kw : internal.get(f).keywords) {
                        tf.merge(kw, 1, Integer::sum);
                    }
                }
                users.add(new SyntheticUser(favs, tf));
            }
            return new ProductIndex(internalSize, docFreq, byKw, extByKw, users);
        }

        List<CatalogProduct> searchInternal(String kw) {
            return internalByKeyword.getOrDefault(kw, Collections.emptyList());
        }

        List<MarketProduct> searchExternal(String kw) {
            List<MarketProduct> p = externalByKeyword.getOrDefault(kw, Collections.emptyList());
            return p.subList(0, Math.min(SERP_POOL_SIZE, p.size()));
        }

        private static Set<String> pick(Random rng, int n) {
            Set<String> s = new LinkedHashSet<>();
            while (s.size() < n) {
                s.add(VOCAB[rng.nextInt(VOCAB.length)]);
            }
            return s;
        }
    }
}
