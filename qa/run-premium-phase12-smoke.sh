#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BASE="even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator"
OUT="qa/out12"
mkdir -p "$OUT"
javac --release 21 -encoding UTF-8 -d "$OUT" \
  "$BASE/population/Vec3.java" "$BASE/population/WaterPoint.java" \
  "$BASE/population/FishKey.java" "$BASE/population/FishVisualModel.java" \
  "$BASE/population/FishAgent.java" "$BASE/population/FishPopulationService.java" \
  "$BASE/school/SchoolSettings.java" "$BASE/school/FishSchool.java" \
  "$BASE/school/FishSchoolService.java" \
  "$BASE/model/FishRenderSpec.java" "$BASE/model/FishModelSettings.java" "$BASE/model/FishPackAudience.java" "$BASE/model/FishRenderPolicy.java" \
  "$BASE/visual/PacketTransport.java" "$BASE/visual/PacketCleanupQueue.java" \
  "$BASE/visual/PacketVisibilityService.java" \
  qa/SimulatorPremiumPhase12Smoke.java
java -ea -cp "$OUT" SimulatorPremiumPhase12Smoke
