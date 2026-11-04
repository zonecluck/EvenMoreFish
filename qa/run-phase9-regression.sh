#!/usr/bin/env bash
# Complete headless/JDK smoke validation, independent of Paper/Gradle.
set -euo pipefail
cd "$(dirname "$0")/.."
for test in \
  qa/run-parse-only.sh \
  qa/run-offline-smoke.sh \
  qa/run-simulator-phase23-smoke.sh \
  qa/run-simulator-phase45-smoke.sh \
  qa/run-simulator-phase6-smoke.sh \
  qa/run-simulator-phase7-smoke.sh \
  qa/run-simulator-phase7-stats-smoke.sh \
  qa/run-simulator-phase8-smoke.sh \
  qa/run-simulator-phase9-smoke.sh \
  qa/run-simulator-phase91-smoke.sh; do
  echo "===== $test ====="
  bash "$test"
done
if command -v python3 >/dev/null && python3 -c 'import yaml' >/dev/null 2>&1; then
  python3 - <<'PY'
from pathlib import Path
import yaml
resources = list(Path('even-more-fish-plugin/src/main/resources').rglob('*.yml')) \
    + list(Path('even-more-fish-plugin-1-21/src/main/resources').rglob('*.yml'))
for file in resources:
    yaml.safe_load(file.read_text(encoding='utf-8'))
locales = list(Path('even-more-fish-plugin/src/main/resources/locales').glob('messages_*.yml'))
for file in locales:
    messages = yaml.safe_load(file.read_text(encoding='utf-8'))['simulator']
    assert {'performance-status', 'doctor-gates', 'doctor-warning'}.issubset(messages)
print(f'PASS: {len(resources)} YAML resources, {len(locales)} locale diagnostics')
PY
else
  echo 'WARNING: PyYAML unavailable; skipped YAML validation (not a production pass).'
fi
printf '\nHEADLESS SUITES PASSED. Gradle full compile and Paper G3/G5/G7 remain UNVERIFIED.\n'
