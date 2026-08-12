# Recommendation Threshold Benchmark Summary

Generated from synthetic benchmarks in:
- `ThresholdSensitivityTest`
- `RecommendationThresholdBenchmarkTest`
- `LargeCatalogRecommendationBenchmarkTest`

Run command:
```bash
mvn test -Dtest=ThresholdSensitivityTest,RecommendationThresholdBenchmarkTest,LargeCatalogRecommendationBenchmarkTest
```

Raw output: `docs/recommendation-threshold-benchmark-results.txt`

## Current production defaults

| Parameter | Value | Meaning |
|---|---|---|
| `top-keyword-count` | 3 | Top-3 TF-IDF keywords drive retrieval |
| `market-supplement-per-keyword` | 3 | Up to 3 SerpAPI items per keyword |
| Max SerpAPI calls | 9 | 3 keywords × 3 items |
| `max-results` | 50 | Final response cap |
| `max-market-results` | 5 | Hard cap on external items (post-PR #8) |
| `skip-market-when-catalog-at-least` | 3 | Skip external search when internal coverage is enough |

Note: `/search` uses `MARKET_SUPPLEMENT_LIMIT=5`; `/recommendation` uses 3.

---

## Key findings at scale (1000 internal + 3000 external, 500 users)

### Config comparison (3×3 vs larger)

| Config | Avg Internal | Avg External | Avg Total | Serp Calls | External share |
|---|---:|---:|---:|---:|---:|
| **3×3 (current)** | 220 | 9 | 229 | 9 | 3.9% |
| 5×5 | 339 | 24 | 363 | 25 | 6.6% |
| 10×10 | 543 | 89 | 631 | 100 | 14.0% |
| 10×20 | 543 | 151 | 693 | 200 | 21.8% |

### Top-K sweep (marketCap fixed at 3)

| TopK | Avg Internal | Marginal | Serp Calls | TF-IDF coverage |
|---:|---:|---:|---:|---:|
| 3 | 220 | — | 9 | 36.3% |
| 5 | 339 | +119 | 15 | 52.4% |
| 8 | 476 | +137 | 24 | 68.3% |
| 11 | 572 | +96 | 33 | 79.0% |

### Market cap sweep (topK fixed at 3, 2000 catalog)

| Market cap | Avg Total | Avg Internal | Avg External | Serp Calls |
|---:|---:|---:|---:|---:|
| 3 | 458.5 | 455.0 | 3.5 | 9 |
| 10 | 459.5 | 455.0 | 4.5 | 30 |
| 50 | 459.5 | 455.0 | 4.5 | 150 |

**Insight:** once catalog is large, increasing market cap barely changes total results. Internal retrieval dominates.

### Catalog size sensitivity (topK=3, marketCap=3)

| Catalog size | Avg Internal | Avg External |
|---:|---:|---:|
| 100 | 18 | 9 |
| 500 | 107 | 9 |
| 1000 | 220 | 9 |
| 5000 | 1142 | 9 |

**Insight:** external count stays flat at ~9; internal hits scale with catalog size.

---

## Interview answer: Why Top-3 external fallback?

### Why 3?

1. **Cost/latency bound:** 3 keywords × 3 items = **9 SerpAPI calls** max.
2. **Catalog-first design:** at 1000 products, internal:external ≈ **24:1**; external is a supplement, not the main source.
3. **Diminishing returns:** market cap 3→50 adds only ~1 extra external item on average (3.5→4.5) while Serp calls go 9→150.
4. **MVP default, not proven optimum:** no A/B test in production; chosen as a pragmatic starting point.

### What if external search returns low relevance?

**Implemented mitigations (PR #8):**
- `market-min-keyword-overlap=1` — filter items with zero keyword overlap
- Item-level TF-IDF score ranking for market results
- `min-item-score=0.15` — drop low-scoring items
- `max-market-results=5` — cap external exposure
- `skip-market-when-catalog-at-least=3` — skip SerpAPI when internal coverage is sufficient
- Low-confidence fallback to popular internal catalog

**Not implemented:** reranking against a learned relevance model or online CTR feedback loop.

### Suggested interview one-liner

> "Three is an engineering default, not an A/B-tested optimum. Benchmarks at 1000 products show internal results dominate (~220 vs ~9 external), so raising market cap mostly adds API cost, not coverage. We mitigate low-relevance external results with keyword-overlap filtering, min-score cutoff, catalog-first ordering, and skipping external search when internal coverage is already sufficient."
