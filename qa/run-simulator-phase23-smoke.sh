#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qa/out
SRC=even-more-fish-plugin/src/main/java/com/oheers/fish
javac -encoding UTF-8 -source 21 -target 21 -d qa/out \
 "$SRC/config/simulator/SimulatorSettings.java" \
 "$SRC/fishing/simulator/zones/ZoneSelection.java" \
 qa/SimulatorPhase23Smoke.java
java -cp qa/out SimulatorPhase23Smoke

javac -encoding UTF-8 -source 21 -target 21 -d qa/out \
 qa/stubs/org/bukkit/inventory/EquipmentSlot.java \
 qa/stubs/org/bukkit/inventory/ItemStack.java \
 "$SRC/fishing/simulator/engine/ProbeSession.java" \
 qa/SimulatorProbeSmoke.java
java -cp qa/out SimulatorProbeSmoke
