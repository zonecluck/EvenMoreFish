#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/presentation
mkdir -p qa/out-premium-service
javac --release 21 -encoding UTF-8 -d qa/out-premium-service \
  qa/stubs10/com/oheers/fish/EvenMoreFish.java \
  qa/stubs10/com/oheers/fish/fishing/simulator/ai/FishAiDirector.java \
  qa/stubs10/com/oheers/fish/fishing/simulator/SimulatorMessages.java \
  qa/stubs10/org/bukkit/Bukkit.java qa/stubs10/org/bukkit/entity/Player.java \
  "$base/PresentationArbiter.java" "$base/UiProfile.java" \
  "$base/VisualPreferenceStore.java" "$base/SimulatorPresentationSettings.java" \
  "$base/FishingPresentationService.java" qa/SimulatorPremiumServiceSmoke.java
java -ea -cp qa/out-premium-service SimulatorPremiumServiceSmoke
