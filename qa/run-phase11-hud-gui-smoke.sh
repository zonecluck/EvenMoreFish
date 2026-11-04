#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qa/out11gui
find qa/stubs11 -name '*.java' > qa/out11gui/sources.txt
cat >> qa/out11gui/sources.txt <<SOURCES
even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/presentation/UiProfile.java
even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/presentation/gui/HudGuiSettings.java
even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/presentation/gui/HudSettingsGui.java
qa/SimulatorHudGuiSmoke.java
SOURCES
javac --release 21 -encoding UTF-8 -d qa/out11gui @qa/out11gui/sources.txt
java -ea -cp qa/out11gui SimulatorHudGuiSmoke
