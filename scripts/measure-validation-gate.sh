#!/usr/bin/env bash
# Times the automated validation gate (Surefire + JaCoCo) and writes
# target/ci-validation-gate.json for comparison against the documented
# manual Postman smoke baseline in docs/METRICS.md.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MANUAL_BASELINE_MS=$((5 * 60 * 1000)) # 5-minute manual Postman + coverage walkthrough
mkdir -p target

START_NS=$(date +%s%N)
mvn -B -q test
END_NS=$(date +%s%N)

AUTOMATED_MS=$(( (END_NS - START_NS) / 1000000 ))
if [[ "$AUTOMATED_MS" -lt 1 ]]; then
  AUTOMATED_MS=1
fi

# Integer percent gain: 100 * (1 - automated/manual)
GAIN_PERCENT=$(( 100 - (AUTOMATED_MS * 100 / MANUAL_BASELINE_MS) ))
if [[ "$GAIN_PERCENT" -lt 0 ]]; then
  GAIN_PERCENT=0
fi

cat > target/ci-validation-gate.json <<EOF
{
  "manualBaselineMs": ${MANUAL_BASELINE_MS},
  "manualBaselineMinutes": 5.0,
  "automatedGateMs": ${AUTOMATED_MS},
  "efficiencyGainPercent": ${GAIN_PERCENT},
  "comparison": "manual Postman smoke + coverage glance vs automated mvn test gate",
  "command": "mvn -B -q test",
  "includes": "JUnit, SearchLatencyBenchmarkTest (>=80% cache hit reduction), JaCoCo, Postman artifact checks"
}
EOF

echo "CI validation gate: automated=${AUTOMATED_MS}ms manualBaseline=${MANUAL_BASELINE_MS}ms gain=${GAIN_PERCENT}%"
cat target/ci-validation-gate.json
