#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash qa/run-premium-phase12-validation.sh
bash qa/run-premium-phase13-smoke.sh
python3 - <<'PY'
from pathlib import Path
import yaml
root=Path('even-more-fish-plugin/src/main/resources')
eco=yaml.safe_load((root/'simulator/ecosystem.yml').read_text(encoding='utf8'))
main=yaml.safe_load((root/'simulator.yml').read_text(encoding='utf8'))
assert eco['config-version']==1 and eco['enabled'] is False
assert eco['water-fx']['enabled'] is False
assert main['simulator']['enabled'] is False
assert main['simulator']['ambient-preview']['enabled'] is False
assert main['simulator']['reward-bridge']['enabled'] is False
assert eco['water-fx']['max-particles-per-viewer']<=eco['water-fx']['max-particles-per-tick']
assert eco['environment']['max-samples-per-tick']<=32
assert 2<=eco['environment']['depth-scan-blocks']<=16
valid={'CALM','CURIOUS','SHY','AGGRESSIVE','NOCTURNAL','DEEP_DWELLER','MYTHIC'}
assert set(eco['personality']['rarity-profiles'].values())<=valid
assert set(eco['personality']['fish-overrides'].values())<=valid
locales=list((root/'locales').glob('messages_*.yml'))
assert len(locales)==18
for path in locales:
    config=yaml.safe_load(path.read_text(encoding='utf8'))
    assert 'ecosystem-status' in config['simulator'],str(path)
print('PASS: Phase13 staging defaults, strict feature flags, 18 locale keys, safe limits')
PY
printf '\nPHASE 13 DOMAIN + REGRESSION VALIDATION COMPLETE. Gradle compile and Paper runtime UNVERIFIED.\n'
