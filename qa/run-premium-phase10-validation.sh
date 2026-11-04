#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash qa/run-phase9-regression.sh
bash qa/run-premium-phase10-smoke.sh
bash qa/run-premium-service-smoke.sh
python3 - <<'PY'
from pathlib import Path
import yaml
root=Path('even-more-fish-plugin/src/main/resources')
files=list(root.rglob('*.yml')) + list(Path('even-more-fish-plugin-1-21/src/main/resources').rglob('*.yml'))
for f in files: yaml.safe_load(f.read_text(encoding='utf-8'))
messages=list((root/'locales').glob('messages_*.yml'))
keys={'presentation-bite','presentation-current','presentation-changed','presentation-status'}
for f in messages:
  data=yaml.safe_load(f.read_text(encoding='utf-8'))
  assert keys.issubset(data['simulator']), f'{f}: missing {keys-data["simulator"].keys()}'
assert yaml.safe_load((root/'simulator.yml').read_text())['simulator']['enabled'] is False
assert yaml.safe_load((root/'simulator/presentation.yml').read_text())['enabled'] is False
assert yaml.safe_load((root/'simulator/effects.yml').read_text())['effects']['enabled'] is False
print(f'PASS: {len(files)} YAML files, {len(messages)} locale sets, all Simulator/Presentation/FX flags OFF')
PY
printf '\nPhase 10 staging tests completed. Does not certify Gradle or Paper runtime.\n'
