#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/presentation
mkdir -p qa/out-premium
javac --release 21 -encoding UTF-8 -d qa/out-premium \
  "$base/PresentationArbiter.java" "$base/UiProfile.java" \
  "$base/VisualPreferenceStore.java" "$base/SimulatorPresentationSettings.java" \
  qa/SimulatorPremiumPhase10Smoke.java
java -ea -cp qa/out-premium SimulatorPremiumPhase10Smoke
