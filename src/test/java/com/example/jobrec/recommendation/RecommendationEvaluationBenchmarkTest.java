package com.example.jobrec.recommendation;

import com.example.jobrec.entity.Item;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Large-scale offline hold-out evaluation and online operational metrics simulation.
 * Produces reproducible numbers for Precision/NDCG@K and production monitoring stats.
 */
class RecommendationEvaluationBenchmarkTest {

    private static final int MAX_RESULTS = 50;
    private static final int TOP_KEYWORD_COUNT = 3;
    private static final int MAX_MARKET_RESULTS = 5;
    private static final int SKIP_MARKET_WHEN_CATALOG_AT_LEAST = 3;
    private static final double MIN_ITEM_SCORE = 0.15;
    private static final int MARKET_MIN_OVERLAP = 1;
    private static final int MARKET_CAP_PER_KEYWORD = 3;
    private static final int MIN_RESULTS_BEFORE_FALLBACK = 3;
    private static final double HOLD_OUT_RATIO = 0.25;
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
    private final RecommendationQualityEvaluator evaluator = new RecommendationQualityEvaluator();

    @Test
    void offlineHoldOutEvaluationAtScale() {
        int[][] scales = {
                {500, 1_000},
                {1_000, 1_000},
                {2_000, 2_000},
                {5_000, 2_000},
        };

        System.out.println("\n=== Offline hold-out evaluation (Precision / Recall / NDCG / HitRate @K) ===");
        System.out.printf("Hold-out ratio=%.2f, K=%d, topKeywords=%d%n%n",
                HOLD_OUT_RATIO, MAX_RESULTS, TOP_KEYWORD_COUNT);
        System.out.println("Catalog  Users  Evaluated  Precision@K  Recall@K  F1@K  NDCG@K  HitRate@K  Coverage");

        for (int[] scale : scales) {
            Catalog catalog = Catalog.build(scale[0], SEED);
            List<SyntheticUser> users = UserFactory.createUsers(catalog, scale[1], SEED);
            HoldOutAggregate agg = runHoldOut(catalog, users);
            System.out.printf("%-7d  %-5d  %-9d  %11.4f  %8.4f  %5.4f  %7.4f  %10.4f  %8.4f%n",
                    scale[0], scale[1], agg.usersEvaluated,
                    agg.precisionAtK, agg.recallAtK, agg.f1AtK, agg.ndcgAtK, agg.hitRateAtK, agg.coverage);
        }

        System.out.println("\n@K=10 slice (same users, 1000 catalog):");
        Catalog catalog = Catalog.build(1_000, SEED);
        List<SyntheticUser> users = UserFactory.createUsers(catalog, 1_000, SEED);
        HoldOutAggregate at10 = runHoldOut(catalog, users, 10);
        System.out.printf("Precision@10=%.4f  Recall@10=%.4f  NDCG@10=%.4f  HitRate@10=%.4f  (n=%d)%n",
                at10.precisionAtK, at10.recallAtK, at10.ndcgAtK, at10.hitRateAtK, at10.usersEvaluated);

        Catalog sanity = Catalog.build(500, SEED);
        assertTrue(runHoldOut(sanity, UserFactory.createUsers(sanity, 200, SEED)).usersEvaluated >= 150);
    }

    @Test
    void onlineOperationalMetricsSimulation() {
        int[][] scales = {
                {120, 1_000},
                {1_000, 1_000},
                {5_000, 2_000},
        };

        System.out.println("\n=== Online operational metrics (simulated production pipeline) ===");
        System.out.println("Catalog  Users  Requests  EmptyRate  ColdStartRate  LowConfRate  AvgScore  CatalogShare  MarketShare  MarketSkippedRate");
        System.out.println("(CTR / favorite rate NOT included — no event simulation)");

        for (int[] scale : scales) {
            Catalog catalog = Catalog.build(scale[0], SEED);
            List<SyntheticUser> users = UserFactory.createUsers(catalog, scale[1], SEED + scale[0]);
            OnlineAggregate agg = runOnlineSimulation(catalog, users);
            System.out.printf("%-7d  %-5d  %-8d  %9.2f%%  %13.2f%%  %11.2f%%  %8.4f  %12.2f%%  %11.2f%%  %17.2f%%%n",
                    scale[0], scale[1], agg.requests,
                    100.0 * agg.emptyRate, 100.0 * agg.coldStartRate, 100.0 * agg.lowConfidenceRate,
                    agg.avgScore, 100.0 * agg.catalogShare, 100.0 * agg.marketShare,
                    100.0 * agg.marketSkippedRate);
        }
    }

    private HoldOutAggregate runHoldOut(Catalog catalog, List<SyntheticUser> users) {
        return runHoldOut(catalog, users, MAX_RESULTS);
    }

    private HoldOutAggregate runHoldOut(Catalog catalog, List<SyntheticUser> users, int k) {
        List<Map<String, Double>> perUser = new ArrayList<>();
        for (SyntheticUser user : users) {
            if (user.favoriteIds.size() < 2) {
                continue;
            }
            List<String> heldOut = evaluator.holdOutTestItems(user.favoriteIds, HOLD_OUT_RATIO);
            Set<String> training = evaluator.trainingFavorites(user.favoriteIds, heldOut);
            Set<String> relevant = new HashSet<>(heldOut);

            List<String> recommended = recommendFromTraining(catalog, training, training, k);
            perUser.add(evaluator.evaluate(recommended, relevant, k));
        }
        Map<String, Double> agg = evaluator.aggregate(perUser);
        return new HoldOutAggregate(
                (int) Math.round(agg.getOrDefault("usersEvaluated", 0.0)),
                agg.getOrDefault("precisionAtK", 0.0),
                agg.getOrDefault("recallAtK", 0.0),
                agg.getOrDefault("f1AtK", 0.0),
                agg.getOrDefault("ndcgAtK", 0.0),
                agg.getOrDefault("hitRateAtK", 0.0),
                agg.getOrDefault("coverage", 0.0)
        );
    }

    /** Catalog-only recommendations for hold-out (labels are internal catalog items). */
    private List<String> recommendFromTraining(Catalog catalog,
                                               Set<String> trainingFavorites,
                                               Set<String> excludeIds,
                                               int limit) {
        Map<String, Integer> termFreq = new HashMap<>();
        for (String favId : trainingFavorites) {
            Product p = catalog.products.get(favId);
            if (p == null) {
                continue;
            }
            for (String kw : p.keywords) {
                termFreq.merge(kw, 1, Integer::sum);
            }
        }
        if (termFreq.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Double> tfidfScores = tfidf.computeScores(termFreq, catalog.docFreq, catalog.size);
        List<String> topKeywords = tfidf.getTopKeywords(tfidfScores, TOP_KEYWORD_COUNT).stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        Map<String, Double> topWeights = tfidf.getTopKeywords(tfidfScores, TOP_KEYWORD_COUNT).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        Set<String> visited = new HashSet<>();
        List<Item> candidates = new ArrayList<>();
        for (String kw : topKeywords) {
            for (Product p : catalog.byKeyword.getOrDefault(kw, Collections.emptyList())) {
                if (excludeIds.contains(p.id) || !visited.add(p.id)) {
                    continue;
                }
                Item item = p.toItem();
                if (itemRanker.scoreItem(item, topWeights) >= MIN_ITEM_SCORE) {
                    candidates.add(item);
                } else {
                    visited.remove(p.id);
                }
            }
        }
        return itemRanker.rankByScore(candidates, topWeights).stream()
                .limit(limit)
                .map(Item::getId)
                .collect(Collectors.toList());
    }

    private OnlineAggregate runOnlineSimulation(Catalog catalog, List<SyntheticUser> users) {
        long requests = 0, empty = 0, coldStart = 0, lowConf = 0, marketSkipped = 0;
        long catalogCount = 0, marketCount = 0;
        double scoreSum = 0;
        int scoreSamples = 0;

        List<Product> popular = catalog.popularProducts(5);

        for (SyntheticUser user : users) {
            requests++;
            SimOutcome outcome;
            if (user.favoriteIds.isEmpty()) {
                outcome = coldStartOutcome(catalog, popular, user.favoriteIds);
                coldStart++;
            } else {
                outcome = personalizedOutcome(catalog, user);
            }

            if (outcome.totalCount == 0) {
                empty++;
            }
            if (outcome.lowConfidenceFallback) {
                lowConf++;
            }
            if (outcome.marketSkipped) {
                marketSkipped++;
            }
            catalogCount += outcome.catalogCount;
            marketCount += outcome.marketCount;
            if (outcome.avgScore > 0) {
                scoreSum += outcome.avgScore;
                scoreSamples++;
            }
        }

        long totalItems = catalogCount + marketCount;
        return new OnlineAggregate(
                requests,
                rate(empty, requests),
                rate(coldStart, requests),
                rate(lowConf, requests),
                scoreSamples == 0 ? 0 : scoreSum / scoreSamples,
                rate(catalogCount, totalItems),
                rate(marketCount, totalItems),
                rate(marketSkipped, requests)
        );
    }

    private SimOutcome coldStartOutcome(Catalog catalog, List<Product> popular, Set<String> exclude) {
        List<Item> results = new ArrayList<>();
        for (Product p : popular) {
            if (!exclude.contains(p.id)) {
                results.add(p.toItem());
            }
        }
        return new SimOutcome(results.size(), 0, results.size(), 0.0, false, false);
    }

    private SimOutcome personalizedOutcome(Catalog catalog, SyntheticUser user) {
        Map<String, Integer> termFreq = new HashMap<>();
        for (String fav : user.favoriteIds) {
            Product p = catalog.products.get(fav);
            if (p == null) {
                continue;
            }
            for (String kw : p.keywords) {
                termFreq.merge(kw, 1, Integer::sum);
            }
        }
        Map<String, Double> tfidfScores = tfidf.computeScores(termFreq, catalog.docFreq, catalog.size);
        Map<String, Double> topWeights = tfidf.getTopKeywords(tfidfScores, TOP_KEYWORD_COUNT).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        List<String> topKeywords = new ArrayList<>(topWeights.keySet());

        Set<String> visited = new HashSet<>();
        List<Item> catalogCandidates = new ArrayList<>();
        for (String kw : topKeywords) {
            for (Product p : catalog.byKeyword.getOrDefault(kw, Collections.emptyList())) {
                if (user.favoriteIds.contains(p.id) || !visited.add(p.id)) {
                    continue;
                }
                Item item = p.toItem();
                if (itemRanker.scoreItem(item, topWeights) >= MIN_ITEM_SCORE) {
                    catalogCandidates.add(item);
                } else {
                    visited.remove(p.id);
                }
            }
        }

        List<Item> rankedCatalog = itemRanker.rankByScore(catalogCandidates, topWeights);
        List<Item> results = new ArrayList<>();
        for (Item item : rankedCatalog) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            results.add(item);
        }

        int catalogInResults = results.size();
        int marketInResults = 0;
        boolean marketSkipped = rankedCatalog.size() >= SKIP_MARKET_WHEN_CATALOG_AT_LEAST;

        if (!marketSkipped) {
            int slots = Math.min(MAX_MARKET_RESULTS, MAX_RESULTS - results.size());
            List<Item> marketCandidates = new ArrayList<>();
            for (String kw : topKeywords) {
                int added = 0;
                for (Product p : catalog.externalByKeyword.getOrDefault(kw, Collections.emptyList())) {
                    if (added >= MARKET_CAP_PER_KEYWORD) {
                        break;
                    }
                    Item item = p.toMarketItem();
                    if (user.favoriteIds.contains(item.getId()) || !visited.add(item.getId())) {
                        continue;
                    }
                    if (itemRanker.countKeywordOverlap(item, topWeights.keySet()) < MARKET_MIN_OVERLAP) {
                        visited.remove(item.getId());
                        continue;
                    }
                    marketCandidates.add(item);
                    added++;
                }
            }
            for (Item item : itemRanker.rankByScore(marketCandidates, topWeights)) {
                if (marketInResults >= slots) {
                    break;
                }
                if (itemRanker.scoreItem(item, topWeights) < MIN_ITEM_SCORE) {
                    continue;
                }
                results.add(item);
                marketInResults++;
            }
        }

        double avgScore = results.isEmpty() ? 0
                : results.stream().mapToDouble(i -> itemRanker.scoreItem(i, topWeights)).average().orElse(0);

        boolean lowConf = results.size() < MIN_RESULTS_BEFORE_FALLBACK || avgScore < MIN_ITEM_SCORE;
        if (lowConf && !results.isEmpty()) {
            List<Product> popular = catalog.popularProducts(5);
            results = new ArrayList<>();
            for (Product p : popular) {
                if (!user.favoriteIds.contains(p.id)) {
                    results.add(p.toItem());
                }
            }
            catalogInResults = results.size();
            marketInResults = 0;
            avgScore = 0;
        }

        return new SimOutcome(results.size(), catalogInResults, marketInResults, avgScore, lowConf, marketSkipped);
    }

    private double rate(long num, long den) {
        return den == 0 ? 0 : (double) num / den;
    }

    private static class HoldOutAggregate {
        final int usersEvaluated;
        final double precisionAtK;
        final double recallAtK;
        final double f1AtK;
        final double ndcgAtK;
        final double hitRateAtK;
        final double coverage;

        HoldOutAggregate(int usersEvaluated, double precisionAtK, double recallAtK, double f1AtK,
                         double ndcgAtK, double hitRateAtK, double coverage) {
            this.usersEvaluated = usersEvaluated;
            this.precisionAtK = precisionAtK;
            this.recallAtK = recallAtK;
            this.f1AtK = f1AtK;
            this.ndcgAtK = ndcgAtK;
            this.hitRateAtK = hitRateAtK;
            this.coverage = coverage;
        }
    }

    private static class OnlineAggregate {
        final long requests;
        final double emptyRate;
        final double coldStartRate;
        final double lowConfidenceRate;
        final double avgScore;
        final double catalogShare;
        final double marketShare;
        final double marketSkippedRate;

        OnlineAggregate(long requests, double emptyRate, double coldStartRate, double lowConfidenceRate,
                        double avgScore, double catalogShare, double marketShare, double marketSkippedRate) {
            this.requests = requests;
            this.emptyRate = emptyRate;
            this.coldStartRate = coldStartRate;
            this.lowConfidenceRate = lowConfidenceRate;
            this.avgScore = avgScore;
            this.catalogShare = catalogShare;
            this.marketShare = marketShare;
            this.marketSkippedRate = marketSkippedRate;
        }
    }

    private static class SimOutcome {
        final int totalCount;
        final int catalogCount;
        final int marketCount;
        final double avgScore;
        final boolean lowConfidenceFallback;
        final boolean marketSkipped;

        SimOutcome(int totalCount, int catalogCount, int marketCount, double avgScore,
                   boolean lowConfidenceFallback, boolean marketSkipped) {
            this.totalCount = totalCount;
            this.catalogCount = catalogCount;
            this.marketCount = marketCount;
            this.avgScore = avgScore;
            this.lowConfidenceFallback = lowConfidenceFallback;
            this.marketSkipped = marketSkipped;
        }
    }

    private static class SyntheticUser {
        final Set<String> favoriteIds;

        SyntheticUser(Set<String> favoriteIds) {
            this.favoriteIds = favoriteIds;
        }
    }

    private static class Product {
        final String id;
        final Set<String> keywords;

        Product(String id, Set<String> keywords) {
            this.id = id;
            this.keywords = keywords;
        }

        Item toItem() {
            return new Item(id, "t-" + id, "s", "1", "src", Item.SOURCE_INNOVA_CATALOG,
                    "d", List.of(), "u", keywords, false);
        }

        Item toMarketItem() {
            return new Item("ext-" + id, "t-" + id, "s", "1", "serp", Item.SOURCE_MARKET,
                    "d", List.of(), "u", keywords, false);
        }
    }

    private static class Catalog {
        final int size;
        final Map<String, Product> products;
        final Map<String, Integer> docFreq;
        final Map<String, List<Product>> byKeyword;
        final Map<String, List<Product>> externalByKeyword;

        Catalog(int size, Map<String, Product> products, Map<String, Integer> docFreq,
                Map<String, List<Product>> byKeyword, Map<String, List<Product>> externalByKeyword) {
            this.size = size;
            this.products = products;
            this.docFreq = docFreq;
            this.byKeyword = byKeyword;
            this.externalByKeyword = externalByKeyword;
        }

        static Catalog build(int catalogSize, long seed) {
            Random rng = new Random(seed);
            Map<String, Product> products = new HashMap<>();
            Map<String, Integer> docFreq = new HashMap<>();
            Map<String, List<Product>> byKeyword = new HashMap<>();

            for (int i = 0; i < catalogSize; i++) {
                Set<String> kws = pick(rng, 4);
                Product p = new Product("in-" + i, kws);
                products.put(p.id, p);
                for (String kw : kws) {
                    docFreq.merge(kw, 1, Integer::sum);
                    byKeyword.computeIfAbsent(kw, k -> new ArrayList<>()).add(p);
                }
            }

            Map<String, List<Product>> extByKw = new HashMap<>();
            int extSize = catalogSize * 3;
            for (int i = 0; i < extSize; i++) {
                Set<String> kws = pick(rng, 3);
                Product p = new Product("e-" + i, kws);
                for (String kw : kws) {
                    extByKw.computeIfAbsent(kw, k -> new ArrayList<>()).add(p);
                }
            }

            return new Catalog(catalogSize, products, docFreq, byKeyword, extByKw);
        }

        List<Product> popularProducts(int limit) {
            return products.values().stream()
                    .sorted(Comparator.comparing(p -> p.id))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        private static Set<String> pick(Random rng, int n) {
            Set<String> s = new LinkedHashSet<>();
            while (s.size() < n) {
                s.add(VOCAB[rng.nextInt(VOCAB.length)]);
            }
            return s;
        }
    }

    private static class UserFactory {
        static List<SyntheticUser> createUsers(Catalog catalog, int userCount, long seed) {
            Random rng = new Random(seed);
            List<SyntheticUser> users = new ArrayList<>(userCount);
            for (int u = 0; u < userCount; u++) {
                int favCount = rng.nextInt(9);
                Set<String> favs = new HashSet<>();
                while (favs.size() < favCount) {
                    favs.add("in-" + rng.nextInt(catalog.size));
                }
                users.add(new SyntheticUser(favs));
            }
            return users;
        }
    }
}
