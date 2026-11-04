#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
mkdir -p qa/out8
javac -encoding UTF-8 -d qa/out8 \
  qa/stubs8/net/kyori/adventure/sound/Sound.java \
  qa/stubs8/org/jetbrains/annotations/Nullable.java \
  qa/stubs8/org/bukkit/Color.java qa/stubs8/org/bukkit/Particle.java qa/stubs8/org/bukkit/World.java \
  qa/stubs8/org/bukkit/Location.java qa/stubs8/org/bukkit/entity/Player.java \
  qa/stubs8/org/bukkit/Bukkit.java \
  qa/stubs8/com/oheers/fish/fishing/simulator/SimulatorMessages.java \
  qa/stubs8/com/oheers/fish/fishing/simulator/presentation/FishingPresentationService.java \
  "$base/presentation/UiProfile.java" \
  "$base/population/FishKey.java" "$base/population/Vec3.java" \
  "$base/population/WaterPoint.java" "$base/population/FishVisualModel.java" \
  "$base/population/FishAgent.java" "$base/population/FishPopulationService.java" \
  "$base/ai/FishAiDirector.java" "$base/model/FishRenderSpec.java" "$base/model/FishModelSettings.java" "$base/model/FishPackAudience.java" "$base/model/FishRenderPolicy.java" \
  "$base/visual/PacketTransport.java" \
  "$base/visual/PacketCleanupQueue.java" \
  "$base/visual/PacketEntityIdAllocator.java" \
  "$base/effects/PremiumCinematicTimeline.java" "$base/effects/PremiumCinematicSettings.java" \
  "$base/effects/CinematicParticleBudget.java" "$base/effects/CinematicParticlePattern.java" "$base/effects/CinematicParticleEffects.java" \
  "$base/effects/CatchArc.java" "$base/effects/SimulatorEffectsSettings.java" \
  "$base/effects/PacketCatchCinematicService.java" "$base/effects/SimulatorEffectsService.java" \
  qa/SimulatorPhase8Smoke.java
java -ea -cp qa/out8 SimulatorPhase8Smoke
