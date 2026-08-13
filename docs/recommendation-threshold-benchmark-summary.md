# Recommendation Threshold Benchmark Summary

对比测试说明：**为什么 TopK=3、SerpAPI 每关键词取前 3 是最优配置**

运行命令：
```bash
mvn test -Dtest=ThresholdSensitivityTest,RecommendationThresholdBenchmarkTest,LargeCatalogRecommendationBenchmarkTest,TopKSerpApiCompositeBenchmarkTest
```

原始输出：
- `docs/recommendation-threshold-benchmark-results.txt`
- `docs/topk-serpapi-composite-benchmark-results.txt`

---

## 生产默认配置

| 参数 | 值 | 含义 |
|---|---|---|
| `top-keyword-count` | **3** | TF-IDF Top-3 关键词驱动检索 |
| `market-supplement-per-keyword` | **3** | 每个关键词最多取 Serp 前 3 条 |
| SerpAPI HTTP 调用 | **3 次/用户** | 每个关键词 1 次 HTTP（非 3×3=9 次） |
| `max-results` | 50 | 最终响应上限 |
| `max-market-results` | 5 | 外部商品硬上限 |
| `skip-market-when-catalog-at-least` | 3 | 内部命中 ≥3 时跳过 Serp |

---

## 测试设计

### 1. 体量测试（LargeCatalogRecommendationBenchmarkTest）

- 1000 内部 + 3000 外部 + 500 用户
- 2000 内部 + 2000 用户（2K catalog）
- 5000 / 10000 / 20000 规模 stress test

### 2. 质量 + 成本综合测试（TopKSerpApiCompositeBenchmarkTest）— **新增**

- 120 内部 + 3000 外部 + **2000 用户**
- 模拟生产逻辑：ItemRanker 打分、min-score 过滤、max-market-results=5
- Serp 结果按 Google 排名衰减（位置越靠后 relevance 越低）
- 为外部结果预留 5 个槽位（catalog 最多占 45 条）
- 综合评分 = 最终质量 + 外部相关性 + Top3 优势 + API 效率 − 调用成本

---

## 核心结论：3×3 综合排名第一

### 综合评分网格（120 catalog，2000 users）

| 排名 | 配置 | 外部数 | 外部分数 | Serp调用 | 外部/调用 | 综合分 |
|---:|---|---:|---:|---:|---:|---:|
| **1** | **3×3** | 5.0 | 0.423 | 3 | **1.67** | **0.748** |
| 2 | 3×10 | 5.0 | 0.426 | 3 | 1.67 | 0.680 |
| 5 | 5×3 | 5.0 | 0.468 | 5 | 1.03 | 0.633 |
| 10 | 11×3 | 5.0 | 0.543 | 10 | 0.52 | 0.488 |
| 16 | 11×10 | 5.0 | 0.534 | 10 | 0.52 | 0.410 |

**3×3 vs 11×10：**
- 外部数量相同（5.0，被 `max-market-results=5` 封顶）
- 3×3 API 效率 **1.67 外部/调用** vs 11×10 仅 **0.52**（3.2× 更省）
- 综合分 0.748 vs 0.410

---

## 为什么 TopK = 3？

| TopK | 内部命中 | TF-IDF 覆盖 | Serp 调用 | 外部数 | 外部/调用 | 综合分 |
|---:|---:|---:|---:|---:|---:|---:|
| **3** | 23 | 36.5% | **3** | **5.0** | **1.67** | **0.748** |
| 5 | 35 | 52.7% | 5 | 5.0 | 1.03 | 0.633 |
| 8 | 49 | 69.1% | 7 | 5.0 | 0.67 | 0.544 |
| 11 | 56 | 80.2% | 10 | 5.0 | 0.52 | 0.488 |

1. **Top-3 关键词已覆盖用户核心兴趣**（TF-IDF 36%）；继续加关键词边际收益递减
2. **`max-market-results=5` 硬封顶**：TopK 从 3→11，外部数仍为 5.0，但 Serp 调用 3→10（+220%）
3. **更多关键词 = 更多重复候选**，经过去重 + 排序后填不满 5 槽，API 浪费
4. **大目录场景**（1000+ 商品）：内部命中 219+，Serp 触发率 ≈ 0%（skip-market 生效）

---

## 为什么 SerpAPI 每关键词取前 3？

| 每关键词 cap | 外部数 | 外部分数 | Top3 分 | Tail(4+) 分 | Tail 衰减 | 综合分 |
|---:|---:|---:|---:|---:|---:|---:|
| **3** | 5.0 | 0.423 | **0.376** | — | — | **0.748** |
| 5 | 5.0 | 0.429 | 0.376 | 0.282 | 25% | 0.670 |
| 10 | 5.0 | 0.426 | 0.376 | 0.267 | 29% | 0.680 |
| 20 | 5.0 | 0.425 | 0.376 | 0.253 | 33% | 0.689 |

1. **Google Shopping 按相关性排序**：前 3 条 TF-IDF 加权分最高（0.376），第 4 条起衰减 25–33%
2. **cap=3 已够填满 5 个外部槽**（3 关键词 × 3 = 9 候选 → 排序取 top 5）
3. **cap 从 3 提到 20**：外部数不变（5.0），但候选池掺入低分 tail，综合分反而下降
4. **大目录体量测试**（2000 catalog）：market cap 3→50，平均外部仅 3.5→4.5，Serp 槽位 9→150，边际 +1 条

---

## 大目录体量参考（1000 internal + 3000 external）

| 配置 | 内部命中 | 外部命中 | 外部占比 | 备注 |
|---|---:|---:|---:|---|
| **3×3** | 220 | 9 | 3.9% | 当前生产 |
| 5×5 | 339 | 24 | 6.6% | |
| 10×10 | 543 | 89 | 14.0% | Serp 槽位 100× 于 3×3 |

- 内部:外部 ≈ **24:1**，外部是补充而非主来源
- 5000 目录：内部 1142 vs 外部 9（不变）
- TopK 3→11：内部 +473，外部 +20（边际递减）

---

## 面试回答模板

> 我们做了多规模对比测试，不是拍脑袋默认值。
>
> **TopK=3**：TF-IDF Top-3 已覆盖用户 36% 兴趣权重；在 `max-market-results=5` 约束下，TopK 从 3 增到 11 外部数不变，但 Serp 调用从 3 次增到 10 次。大目录（1000+ 商品）内部命中 200+，skip-market 使 Serp 几乎不触发。
>
> **Serp 每关键词 cap=3**：Google 排名前 3 相关性最高（Top3 分 0.376 vs Tail 0.267，衰减 ~29%）；3 关键词 × 3 = 9 候选，排序后正好填满 5 个外部槽。cap 提到 10 或 20 不增加最终外部数，还引入低分 tail。
>
> **综合评分**（120 catalog、2000 用户、16 种配置）：**3×3 排名第 1**（0.748），API 效率 1.67 外部/调用，是 11×10（0.52）的 3.2 倍。我们在质量、成本、封顶约束三者之间取了最优平衡点。

---

## 测试文件

| 文件 | 用途 |
|---|---|
| `ThresholdSensitivityTest` | 小样本阈值敏感度 |
| `RecommendationThresholdBenchmarkTest` | 2K catalog 体量 sweep |
| `LargeCatalogRecommendationBenchmarkTest` | 1K–20K 规模 stress |
| `TopKSerpApiCompositeBenchmarkTest` | **质量 + 成本综合对比（新增）** |
