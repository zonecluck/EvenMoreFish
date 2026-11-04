#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BASE="even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator"
OUT="qa/out13"
mkdir -p "$OUT"
javac --release 21 -encoding UTF-8 -d "$OUT" \
  "$BASE/population/Vec3.java" "$BASE/population/WaterPoint.java" \
  "$BASE/population/FishKey.java" "$BASE/population/FishVisualModel.java" \
  "$BASE/population/FishAgent.java" "$BASE/population/FishPopulationService.java" \
  "$BASE/behavior/FishPersonality.java" "$BASE/behavior/FishPersonalityService.java" \
  "$BASE/environment/EnvironmentSnapshot.java" "$BASE/environment/EnvironmentSampler.java" \
  "$BASE/environment/EcosystemSettings.java" \
  "$BASE/waterfx/SurfaceFxService.java" qa/SimulatorPremiumPhase13Smoke.java
java -ea -cp "$OUT" SimulatorPremiumPhase13Smoke
javac --release 21 -encoding UTF-8 -cp "$OUT" -d "$OUT" \
  qa/stubs13/org/bukkit/configuration/InvalidConfigurationException.java \
  qa/stubs13/org/bukkit/configuration/ConfigurationSection.java \
  qa/stubs13/org/bukkit/configuration/file/YamlConfiguration.java \
  qa/stubs13/com/oheers/fish/EvenMoreFish.java \
  "$BASE/environment/EcosystemConfigLoader.java" qa/SimulatorPremiumPhase13ConfigSmoke.java
java -ea -cp "$OUT" SimulatorPremiumPhase13ConfigSmoke
