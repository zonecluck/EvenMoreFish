#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BASE=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator
OUT=qa/out14
mkdir -p "$OUT"
javac --release 21 -encoding UTF-8 -d "$OUT" \
  "$BASE/population/Vec3.java" "$BASE/population/WaterPoint.java" \
  "$BASE/population/FishKey.java" "$BASE/population/FishVisualModel.java" \
  "$BASE/population/FishAgent.java" "$BASE/population/FishPopulationService.java" \
  "$BASE/environment/EnvironmentSnapshot.java" \
  "$BASE/encounter/RareEncounterSettings.java" "$BASE/encounter/RareEncounterDirector.java" \
  qa/SimulatorPremiumPhase14Smoke.java
java -ea -cp "$OUT" SimulatorPremiumPhase14Smoke
# Compile the real YAML loader with explicit Bukkit stubs; this is not a full Paper compile.
javac --release 21 -encoding UTF-8 -cp "$OUT" -d "$OUT" \
  qa/stubs14/org/bukkit/configuration/InvalidConfigurationException.java \
  qa/stubs14/org/bukkit/configuration/ConfigurationSection.java \
  qa/stubs14/org/bukkit/configuration/file/YamlConfiguration.java \
  qa/stubs14/com/oheers/fish/EvenMoreFish.java \
  "$BASE/encounter/RareEncounterConfigLoader.java" qa/SimulatorPremiumPhase14ConfigSmoke.java
java -ea -cp "$OUT" SimulatorPremiumPhase14ConfigSmoke
# Execute JUnit-shaped source tests via a local assertion harness; real JUnit/Gradle remains unverified.
javac --release 21 -encoding UTF-8 -cp "$OUT" -d "$OUT" \
  qa/stubs14/org/junit/jupiter/api/Test.java qa/stubs14/org/junit/jupiter/api/Assertions.java \
  even-more-fish-plugin/src/test/java/com/oheers/fish/fishing/simulator/encounter/RareEncounterDirectorTest.java \
  qa/RunPremiumPhase14JunitShapedTests.java
java -cp "$OUT" RunPremiumPhase14JunitShapedTests
