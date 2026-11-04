#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BASE=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
OUT=qa/out15
mkdir -p "$OUT"
javac --release 21 -encoding UTF-8 -d "$OUT" \
  "$BASE/population/Vec3.java" "$BASE/population/WaterPoint.java" \
  "$BASE/population/FishKey.java" "$BASE/population/FishVisualModel.java" \
  "$BASE/population/FishAgent.java" \
  "$BASE/model/FishRenderSpec.java" "$BASE/model/FishModelSettings.java" \
  "$BASE/model/FishPackAudience.java" "$BASE/model/FishRenderPolicy.java" \
  "$BASE/visual/PacketTransport.java" "$BASE/visual/PacketCleanupQueue.java" \
  "$BASE/visual/PacketVisibilityService.java" qa/SimulatorPremiumPhase15Smoke.java
java -ea -cp "$OUT" SimulatorPremiumPhase15Smoke
# Compile the real config parser against minimal Bukkit API stubs: NOT a full Paper compile.
javac --release 21 -encoding UTF-8 -cp "$OUT" -d "$OUT" \
  qa/stubs15/org/bukkit/configuration/InvalidConfigurationException.java \
  qa/stubs15/org/bukkit/configuration/ConfigurationSection.java \
  qa/stubs15/org/bukkit/configuration/file/YamlConfiguration.java \
  qa/stubs15/org/bukkit/Material.java \
  qa/stubs15/com/oheers/fish/EvenMoreFish.java \
  "$BASE/model/FishModelConfigLoader.java" qa/SimulatorPremiumPhase15ConfigSmoke.java
java -ea -cp "$OUT" SimulatorPremiumPhase15ConfigSmoke
