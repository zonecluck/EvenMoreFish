#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qa/out
javac -encoding UTF-8 -d qa/out qa/ParseAllJava.java
java -cp qa/out ParseAllJava
