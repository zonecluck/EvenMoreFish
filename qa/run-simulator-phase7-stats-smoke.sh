#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qa/out7stats
javac -encoding UTF-8 -d qa/out7stats \
  qa/stubs7/org/bukkit/entity/Player.java \
  qa/stubs7/com/oheers/fish/fishing/items/Fish.java \
  qa/stubs7/com/oheers/fish/api/events/EMFFishCaughtEvent.java \
  even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/commit/SimulatorCatchEventGate.java \
  qa/SimulatorPhase7StatsGateSmoke.java
java -ea -cp qa/out7stats SimulatorPhase7StatsGateSmoke
