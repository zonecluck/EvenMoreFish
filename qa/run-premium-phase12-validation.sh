#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash qa/run-premium-phase11-validation.sh
bash qa/run-premium-phase12-smoke.sh
python3 - <<'PY'
from pathlib import Path
import yaml
root=Path('even-more-fish-plugin/src/main/resources')
base=yaml.safe_load((root/'simulator.yml').read_text(encoding='utf-8'))
config=yaml.safe_load((root/'simulator/schooling.yml').read_text(encoding='utf-8'))
assert config['config-version']==1 and config['enabled'] is False
assert base['simulator']['enabled'] is False
assert base['simulator']['ambient-preview']['enabled'] is False
assert base['simulator']['reward-bridge']['enabled'] is False
assert config['limits']['max-members']<=8
assert config['limits']['max-schools-per-zone']<=config['limits']['max-schools']
assert config['limits']['decisions-per-move']<=config['limits']['neighbor-checks-per-move']
assert 0 < config['movement']['leader-speed'] <= config['movement']['follower-speed'] <= .5
locales=list((root/'locales').glob('messages_*.yml'))
assert len(locales)==18
for path in locales:
    msg=yaml.safe_load(path.read_text(encoding='utf-8'))
    assert 'schooling-status' in msg['simulator'],path
print('PASS: Phase12 schooling.yml immutable staging defaults, 18 locales, budget constraints')
PY
printf '\nPHASE 12 SOURCE/HEADLESS VALIDATION PASSED. Gradle compile and Paper runtime remain UNVERIFIED.\n'
