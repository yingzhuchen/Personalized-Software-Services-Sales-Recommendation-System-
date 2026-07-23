# Measured resume / impact metrics

This document records **how** the latency and CI-validation claims are measured so numbers stay auditable.

## 1. Search latency reduction (≥80%)

| Item | Value |
|------|--------|
| **Scope** | `RecommendationService.searchProducts` only (not full HTTP `/search`) |
| **Hit path** | Real Redis `GET` + JSON parse |
| **Miss baseline** | Real MySQL catalog `LIKE` search + market stand-in (~80ms SerpAPI RTT; no API key in CI) |
| **Formula** | `1 - p50(hitMs) / p50(missMs)` |
| **Unit / mock regression** | `SearchLatencyBenchmarkTest` (deterministic delays) |
| **Real-store integration** | `SearchLatencyRedisMySqlIntegrationTest` (local MySQL `jobrec_it` + Redis) |
| **Live metrics** | `GET /cache/metrics` → `searchLatency` |
| **Latest real-store run** | hit p50 ≈ **0.73ms**, miss p50 ≈ **85.47ms**, reduction ≈ **99.1%** |

### Real MySQL + Redis protocol

1. Create DB/user once: `jobrec_it` / `jobrec` / `jobrec` on `127.0.0.1:3306`; Redis on `127.0.0.1:6379`.
2. Test seeds catalog products into MySQL (default **500** extras) and clears `search:*` Redis keys.
3. Cold miss writes search JSON into Redis; subsequent hits read Redis only.
4. Fresh miss samples delete the Redis key each iteration so MySQL runs again.
5. Assert reduction ≥ 0.80.
6. Reports written to `target/search-latency-standard.json` and `target/search-latency-large.json`.

```bash
# standard (~50 samples/path)
mvn -Dtest=SearchLatencyRedisMySqlIntegrationTest#realRedisHitIsAtLeast80PercentFasterThanMysqlPlusMarketMiss test

# large load (default 500 samples/path, ~500 catalog extras)
mvn -Dtest=SearchLatencyRedisMySqlIntegrationTest#realStoreLargeLoadBenchmark test
# or:
./scripts/run-search-latency-load.sh

# even larger
SEARCH_LATENCY_LARGE_SAMPLES=1000 SEARCH_LATENCY_EXTRA_PRODUCTS=3000 ./scripts/run-search-latency-load.sh
# equivalent:
mvn -Dtest=SearchLatencyRedisMySqlIntegrationTest#realStoreLargeLoadBenchmark \
  -Dsearch.latency.largeSamples=1000 -Dsearch.latency.extraProducts=3000 test

cat target/search-latency-large.json
```

Tunable system properties: `search.latency.samples`, `search.latency.largeSamples`, `search.latency.extraProducts`, `search.latency.warmup`.

If MySQL/Redis are down, the IT is skipped via `@EnabledIf`.


## 2. Pre-deploy validation efficiency (≥30%)

| Item | Value |
|------|--------|
| **Manual baseline** | ~**5 minutes** — walk through mission-critical Postman collection (`innova-product-api`) + glance at coverage |
| **Automated gate** | `mvn test` (JUnit, JaCoCo, latency regression, Postman artifact checks) |
| **Measure script** | `scripts/measure-validation-gate.sh` → `target/ci-validation-gate.json` |
| **CI wiring** | `Jenkinsfile`, `.gitlab-ci.yml` |
| **Latest CI run** | automated ≈ **28s**, gain ≈ **91%** vs 5-minute manual smoke |

```bash
./scripts/measure-validation-gate.sh
cat target/ci-validation-gate.json
```

**Resume wording note:** “boosting deployment efficiency by 30%” is a **conservative** claim relative to the measured pre-deploy validation-gate speedup (~90%). Prefer phrasing it as validation / regression-gate time if interviewers ask for the baseline.

## 3. What is *not* claimed here

- End-to-end `/search` HTTP latency (favorites MySQL still runs on every request).
- Full production deploy wall-clock (image push, EKS rollout) — not timed in this repo.
- The older README “~30% API latency” figure — replaced by the ≥80% **searchProducts** cache-hit measurement above.
