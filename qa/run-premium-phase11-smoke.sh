#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
mkdir -p qa/out11
javac --release 21 -encoding UTF-8 -d qa/out11 \
  qa/stubs8/net/kyori/adventure/sound/Sound.java \
  qa/stubs8/org/jetbrains/annotations/Nullable.java \
  qa/stubs8/org/bukkit/Color.java qa/stubs8/org/bukkit/Particle.java qa/stubs8/org/bukkit/World.java \
  qa/stubs8/org/bukkit/Location.java qa/stubs8/org/bukkit/entity/Player.java qa/stubs8/org/bukkit/Bukkit.java \
  "$base/population/FishKey.java" "$base/population/Vec3.java" \
  "$base/population/WaterPoint.java" "$base/population/FishVisualModel.java" "$base/population/FishAgent.java" \
  "$base/model/FishRenderSpec.java" "$base/model/FishModelSettings.java" "$base/model/FishPackAudience.java" "$base/model/FishRenderPolicy.java" \
  "$base/visual/PacketTransport.java" "$base/visual/PacketCleanupQueue.java" \
  "$base/visual/PacketEntityIdAllocator.java" "$base/effects/CatchArc.java" \
  "$base/effects/PremiumCinematicTimeline.java" "$base/effects/PremiumCinematicSettings.java" \
  "$base/effects/SimulatorEffectsSettings.java" "$base/effects/PacketCatchCinematicService.java" \
  qa/SimulatorPremiumPhase11Smoke.java
java -ea -cp qa/out11 SimulatorPremiumPhase11Smoke
