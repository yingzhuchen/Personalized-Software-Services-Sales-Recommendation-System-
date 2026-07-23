# Measured resume / impact metrics

This document records **how** the latency and CI-validation claims are measured so numbers stay auditable.

## 1. Search latency reduction (≥80%)

| Item | Value |
|------|--------|
| **Scope** | `RecommendationService.searchProducts` only (not full HTTP `/search`) |
| **Hit path** | Redis get + JSON parse |
| **Miss baseline** | MySQL catalog + SerpAPI / EdenAI (uncached product search) |
| **Formula** | `1 - p50(hitMs) / p50(missMs)` |
| **Regression test** | `SearchLatencyBenchmarkTest` |
| **Live metrics** | `GET /cache/metrics` → `searchLatency` (`hitP50Ms`, `missP50Ms`, `latencyReductionPercent`) |
| **Latest CI run** | hit p50 ≈ **4.35ms**, miss p50 ≈ **81.70ms**, reduction ≈ **94.7%** |

Benchmark protocol (deterministic in CI):

1. Inject ~4ms delay on Redis hit collaborator; ~80ms delay on miss/search collaborator (stands in for catalog + external APIs).
2. Warm up 3 iterations, reset counters, then collect 25 hit + 25 miss samples.
3. Assert reduction ≥ 0.80.

Production traffic is also timed in-process via `SearchLatencyMetrics` on every `searchProducts` call.

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
