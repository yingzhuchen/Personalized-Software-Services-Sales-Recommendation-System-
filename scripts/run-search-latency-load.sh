#!/usr/bin/env bash
# Large real-store search latency load against local MySQL + Redis.
# Writes target/search-latency-large.json
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SAMPLES="${SEARCH_LATENCY_LARGE_SAMPLES:-500}"
PRODUCTS="${SEARCH_LATENCY_EXTRA_PRODUCTS:-2000}"
WARMUP="${SEARCH_LATENCY_WARMUP:-20}"

echo "Running large search latency load: samples=${SAMPLES} products=${PRODUCTS} warmup=${WARMUP}"
mvn -B -Dtest=SearchLatencyRedisMySqlIntegrationTest#realStoreLargeLoadBenchmark \
  -Dsearch.latency.largeSamples="${SAMPLES}" \
  -Dsearch.latency.extraProducts="${PRODUCTS}" \
  -Dsearch.latency.warmup="${WARMUP}" \
  test

echo "Report:"
cat target/search-latency-large.json
