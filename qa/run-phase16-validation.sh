#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# This script validates source/domain behavior only, not Paper runtime.
bash qa/run-premium-phase15-validation.sh
bash qa/run-phase16-smoke.sh
python3 - <<'PY'
from pathlib import Path
import yaml
resources=Path('even-more-fish-plugin/src/main/resources')
master=yaml.safe_load((resources/'simulator.yml').read_text(encoding='utf-8'))['simulator']
assert master['enabled'] is False
assert master['native-hook-probe']['enabled'] is False
assert master['ambient-preview']['enabled'] is False
assert master['ai-preview']['enabled'] is False
assert master['reward-bridge']['enabled'] is False
assert master['reward-bridge']['paper-gates-verified'] is False
for path in resources.glob('simulator/*.yml'):
    document=yaml.safe_load(path.read_text(encoding='utf-8'))
    if isinstance(document,dict) and 'enabled' in document:
        assert document['enabled'] is False, f'{path} must default OFF'
print('PASS Phase16 staging flags: Simulator, preview, AI, native probe, reward gate and all optional FX OFF')
PY
printf '\nSOURCE/HEADLESS PASS. No Gradle build or Paper runtime has been certified.\n'
