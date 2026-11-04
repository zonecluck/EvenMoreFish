#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash qa/run-premium-phase14-validation.sh
bash qa/run-premium-phase15-smoke.sh
python3 - <<'PY'
from pathlib import Path
import yaml, json
res=Path('even-more-fish-plugin/src/main/resources')
model=yaml.safe_load((res/'simulator/models.yml').read_text(encoding='utf8'))
assert model['config-version']==1 and model['enabled'] is False
assert model['fish-overrides']=={} and model['rarity-overrides']=={}
main=yaml.safe_load((res/'simulator.yml').read_text(encoding='utf8'))
assert main['simulator']['enabled'] is False
assert main['simulator']['reward-bridge']['enabled'] is False
assert main['simulator']['reward-bridge']['paper-gates-verified'] is False
pack=Path('docs/simulator/resource-pack-example')
assert json.loads((pack/'pack.mcmeta').read_text())['pack']['pack_format']==46
assert json.loads((pack/'assets/ledat/items/fish/golden_koi.json').read_text())['model']['model']=='ledat:item/fish/golden_koi'
assert json.loads((pack/'assets/ledat/models/item/fish/golden_koi.json').read_text())['parent']=='minecraft:item/generated'
yamls=list(res.rglob('*.yml'))+list(Path('even-more-fish-plugin-1-21/src/main/resources').rglob('*.yml'))
for file in yamls: yaml.safe_load(file.read_text(encoding='utf-8'))
locales=list((res/'locales').glob('messages_*.yml'))
assert len(locales)==18
for file in locales:
    messages=yaml.safe_load(file.read_text(encoding='utf-8'))['simulator']
    assert 'model-status' in messages,file
print('PASS Phase15 config flags OFF, 18 locale model-status, example resource pack JSON, '+str(len(yamls))+' YAML files')
PY
printf '\nPHASE 15 HEADLESS VALIDATION COMPLETE. Gradle full compile and Paper G3/G5/G7/G15 UNVERIFIED.\n'
