#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Full legacy regression is the first prerequisite.
bash qa/run-premium-phase10-validation.sh
bash qa/run-premium-phase11-smoke.sh
bash qa/run-phase11-hud-gui-smoke.sh
python3 - <<'PY'
from pathlib import Path
import yaml
root=Path('even-more-fish-plugin/src/main/resources')
scenes=yaml.safe_load((root/'simulator/cinematics.yml').read_text(encoding='utf8'))
gui=yaml.safe_load((root/'simulator/hud-gui.yml').read_text(encoding='utf8'))
fx=yaml.safe_load((root/'simulator/effects.yml').read_text(encoding='utf8'))
ui=yaml.safe_load((root/'simulator/presentation.yml').read_text(encoding='utf8'))
engine=yaml.safe_load((root/'simulator.yml').read_text(encoding='utf8'))
assert scenes['config-version']==1 and scenes['enabled'] is False
assert gui['config-version']==1 and gui['enabled'] is False
assert fx['effects']['enabled'] is False and fx['cinematic']['enabled'] is False
assert ui['enabled'] is False and engine['simulator']['enabled'] is False
assert set(scenes['presets'])=={'common','rare','epic','legendary'}
for name,profile in scenes['presets'].items():
    stages=profile['stages'];duration=sum(stages.values())
    assert 8<=duration<=90,(name,duration)
    assert stages['surface']>=2 and stages['delivery']>=2 and stages['fade']>=1
    assert set(profile['cues'])=={'anticipation','vortex','surface_break','hero_reveal','delivery','fade'}
    assert 0<=profile['arc-height']<=3 and 0<=profile['orbit-width']<=1.5
assert gui['size'] in range(9,55,9)
slots=[b['slot'] for b in gui['buttons'].values()]
assert len(slots)==len(set(slots))==5
assert all(0<=v<gui['size'] for v in slots)
keys={'cinematic-reveal-title','cinematic-reveal-subtitle','cinematic-preview-invalid',
 'cinematic-preview-water','cinematic-preview-started','cinematic-preview-unavailable',
 'cinematic-preview-stopped','premium-status'}
for loc in (root/'locales').glob('messages_*.yml'):
    config=yaml.safe_load(loc.read_text(encoding='utf8'))
    assert keys.issubset(config['simulator']),str(loc)
print('PASS Phase 11 config: 6-stage presets, immutable gate defaults, 18 locale keys, GUI slot uniqueness')
PY
printf '\nPHASE 10B+11 HEADLESS VALIDATION PASSED. Full Gradle compile and Paper runtime remain BLOCKED/UNVERIFIED.\n'
