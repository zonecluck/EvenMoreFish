#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qa/out9
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
javac -encoding UTF-8 -d qa/out9 \
  "$base/population/FishKey.java" "$base/population/WaterPoint.java" \
  "$base/population/Vec3.java" "$base/population/FishVisualModel.java" \
  "$base/population/FishAgent.java" "$base/population/FishPopulationService.java" \
  "$base/visual/PacketEntityIdAllocator.java" "$base/model/FishRenderSpec.java" "$base/model/FishModelSettings.java" "$base/model/FishPackAudience.java" "$base/model/FishRenderPolicy.java" \
  "$base/visual/PacketTransport.java" \
  "$base/visual/PacketCleanupQueue.java" \
  "$base/visual/PacketVisibilityService.java" qa/SimulatorPhase9Smoke.java
java -ea -cp qa/out9 SimulatorPhase9Smoke
