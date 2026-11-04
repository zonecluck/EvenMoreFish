#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
out=qa/out16
mkdir -p "$out"
javac --release 21 -encoding UTF-8 -d "$out" \
  "$base/population/Vec3.java" "$base/population/WaterPoint.java" \
  "$base/population/FishKey.java" "$base/population/FishVisualModel.java" \
  "$base/population/FishAgent.java" "$base/model/FishRenderSpec.java" \
  "$base/model/FishModelSettings.java" "$base/model/FishPackAudience.java" \
  "$base/model/FishRenderPolicy.java" \
  "$base/visual/PacketTransport.java" "$base/visual/PacketCleanupQueue.java" \
  "$base/visual/PacketVisibilityService.java" \
  qa/SimulatorPhase16Smoke.java
java -ea -cp "$out" SimulatorPhase16Smoke
