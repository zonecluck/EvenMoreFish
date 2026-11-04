#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qa/out
SRC=even-more-fish-plugin/src/main/java/com/oheers/fish
javac -encoding UTF-8 -source 21 -target 21 -d qa/out \
 qa/stubs/org/jetbrains/annotations/NotNull.java \
 qa/stubs/org/jetbrains/annotations/Nullable.java \
 qa/stubs/com/oheers/fish/EvenMoreFish.java \
 "$SRC/database/data/strategy/DataSavingStrategy.java" \
 "$SRC/database/data/strategy/BufferedSavingStrategy.java" \
 "$SRC/database/data/manager/DataManager.java" \
 "$SRC/database/execute/DbExecutor.java" \
 "$SRC/fishing/items/RegionWeightMultiplier.java" \
 "$SRC/utils/WeightedRandom.java" \
 qa/OfflineSmoke.java
java -cp qa/out OfflineSmoke
