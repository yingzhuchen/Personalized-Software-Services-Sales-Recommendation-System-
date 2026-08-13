# Recommendation Evaluation Benchmark Results

Synthetic large-scale evaluation. Run:

```bash
mvn test -Dtest=RecommendationEvaluationBenchmarkTest
```

Raw output: `docs/recommendation-evaluation-benchmark-results.txt`

Test class: `RecommendationEvaluationBenchmarkTest`

---

## 1. Offline hold-out（有结果 ✅）

**方法：** 每用户藏 25% 收藏（至少 1 条），用其余收藏建 TF-IDF 画像，生成 Top-50 推荐，以被藏收藏为 relevant label。指标由 `RecommendationQualityEvaluator` 计算，对用户取平均。

**配置：** hold-out ratio=0.25, K=50, topKeywords=3, catalog-only（标签均为内部商品）

| Catalog | Users | Evaluated | Precision@50 | Recall@50 | F1@50 | NDCG@50 | HitRate@50 | Coverage |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 500 | 1000 | 769 | 0.0024 | 0.0800 | 0.0047 | **0.0208** | **0.1118** | 1.0000 |
| 1000 | 1000 | 762 | 0.0011 | 0.0367 | 0.0021 | 0.0128 | 0.0551 | 1.0000 |
| 2000 | 2000 | 1533 | 0.0004 | 0.0134 | 0.0009 | 0.0043 | 0.0215 | 1.0000 |
| 5000 | 2000 | 1533 | 0.0004 | 0.0104 | 0.0007 | 0.0028 | 0.0176 | 1.0000 |

**@K=10（1000 catalog，762 users）：**

| Precision@10 | Recall@10 | NDCG@10 | HitRate@10 |
|---:|---:|---:|---:|
| 0.0026 | 0.0184 | 0.0080 | 0.0262 |

**解读：**
- Precision@K 低是因为 K=50 大、目录商品多，分母大
- **HitRate@50** 更直观：500 目录时 **11.2%** 用户至少命中 1 条被藏收藏
- **NDCG@50** 随目录增大下降（竞争候选增多）
- Coverage=1.0：有收藏的用户都能产生非空推荐

---

## 2. Online operational metrics（有结果 ✅，合成模拟）

**方法：** 模拟生产 pipeline（TF-IDF + ItemRanker + skip-market + fallback），对每用户记录运营指标。**非真实生产流量。**

| Catalog | Users | EmptyRate | ColdStartRate | LowConfRate | AvgScore | CatalogShare | MarketShare | MarketSkippedRate |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 120 | 1000 | 0% | 11.6% | 0% | 0.426 | 97.2% | 2.8% | 88.4% |
| 1000 | 1000 | 0% | 11.2% | 0% | 0.584 | 98.8% | 1.2% | 88.8% |
| 5000 | 2000 | 0% | 10.4% | 0% | 0.860 | 98.9% | 1.2% | 89.6% |

**解读：**
- ~11% 用户零收藏 → cold-start 热门兜底
- 大目录下 **~89% 请求跳过 Serp**（内部命中 ≥3）
- 内部商品占最终列表 **97–99%**
- 空结果率 0%（有兜底）

---

## 3. 没有结果、面试不要提 ❌

| 指标 | 原因 |
|---|---|
| **CTR / favorite rate** | 未模拟用户点击/收藏事件，无埋点数据 |
| **真实 MySQL 用户 hold-out** | 本次为合成目录+合成用户；`/evaluate` 端点需真实 DB |
| **在线 A/B 对比** | 未做 |

---

## 面试可用话术（仅说有结果的）

**Offline（有数字）：**
> "We ran hold-out evaluation on up to 2000 synthetic users. At 500-catalog scale, HitRate@50 is 11.2% and NDCG@50 is 0.021 — low Precision@50 is expected with K=50 and a large candidate pool. We use NDCG and HitRate as primary offline signals."

**Online（有数字，注明 simulated）：**
> "In our simulated production pipeline, ~11% of requests hit cold-start fallback, ~89% skip Serp when catalog coverage is sufficient, and catalog items are 97–99% of final results. We track avg score and empty rate — currently 0% empty with fallback enabled. CTR is not reported yet; events aren't wired."
