#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
mkdir -p qa/out91
javac -encoding UTF-8 -d qa/out91 \
  qa/stubs91/org/bukkit/inventory/ItemStack.java \
  "$base/population/FishKey.java" "$base/population/WaterPoint.java" \
  "$base/population/Vec3.java" "$base/population/FishVisualModel.java" \
  "$base/population/FishAgent.java" "$base/model/FishRenderSpec.java" "$base/model/FishModelSettings.java" "$base/model/FishPackAudience.java" "$base/model/FishRenderPolicy.java" \
  "$base/visual/PacketTransport.java" \
  "$base/visual/PacketCleanupQueue.java" \
  "$base/commit/SingleSlotDeliveryPlanner.java" qa/SimulatorPhase91Smoke.java
java -ea -cp qa/out91 SimulatorPhase91Smoke
