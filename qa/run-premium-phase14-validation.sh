#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash qa/run-premium-phase13-validation.sh
bash qa/run-premium-phase14-smoke.sh
python3 - <<'PY'
from pathlib import Path
import yaml
root=Path('even-more-fish-plugin/src/main/resources')
enc=yaml.safe_load((root/'simulator/encounters.yml').read_text(encoding='utf-8'))
main=yaml.safe_load((root/'simulator.yml').read_text(encoding='utf-8'))
assert enc['config-version']==1 and enc['enabled'] is False
assert main['simulator']['enabled'] is False
assert main['simulator']['ambient-preview']['enabled'] is False
assert main['simulator']['reward-bridge']['enabled'] is False
assert main['simulator']['reward-bridge']['paper-gates-verified'] is False
assert enc['limits']['max-per-zone']<=enc['limits']['max-active']
assert enc['visuals']['max-particles-per-viewer']<=enc['visuals']['max-particles-per-tick']
assert 0<=enc['discovery']['activation-chance']<=1
assert set(enc['types'])=={'RARE_PRESENCE','GOLDEN','SHADOW','GHOST','RARE_SCHOOL'}
assert all(entry['min-school-size'] in range(1,5) for entry in enc['types'].values())
files=list(root.rglob('*.yml'))+list(Path('even-more-fish-plugin-1-21/src/main/resources').rglob('*.yml'))
for p in files: yaml.safe_load(p.read_text(encoding='utf8'))
locales=list((root/'locales').glob('messages_*.yml'))
assert len(locales)==18
for path in locales:
    msg=yaml.safe_load(path.read_text(encoding='utf8'))['simulator']
    assert {'encounter-status','encounter-invalid','encounter-preview-ok','encounter-preview-unavailable'}<=msg.keys(),path
print(f'PASS: {len(files)} YAML resources, {len(locales)} locales, Phase14 OFF by default, zero-public-reward policy')
PY
printf '\nPHASE 14 HEADLESS VALIDATION COMPLETE. Gradle compile and Paper G3/G5/G7 remain UNVERIFIED.\n'
