#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
mkdir -p qa/out6
javac -encoding UTF-8 -d qa/out6 \
  "$base/population/FishKey.java" "$base/population/WaterPoint.java" \
  "$base/population/Vec3.java" "$base/population/FishVisualModel.java" \
  "$base/population/FishAgent.java" "$base/population/FishPopulationService.java" \
  "$base/ai/FishAiDirector.java" qa/SimulatorPhase6Smoke.java
java -ea -cp qa/out6 SimulatorPhase6Smoke
