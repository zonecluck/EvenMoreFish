# Baseline verification

Date: 2026-10-12. Context: `C:/Users/hp/IdeaProjects/MineBox/EvenMoreFish`.

Required first all-variant build passed before hook countdown source changes: `gradlew.bat --project-cache-dir qa/gradle-cache build -x test`, exit 0, BUILD SUCCESSFUL in 2s; all 38 tasks up-to-date. Core, addons and 1.20/1.21/26.1 variants passed. [Log](baseline-build.log).

The current 1.21 JAR hash matches the preceding verified cinematic build: `c9db5e1500fd4f80f3b87909c1697b4ab2266346f09d26c29b2f8fbd95eae64d`. A local baseline JAR copy and SHA file are preserved in this report directory as rollback/evidence artifacts.

The preceding actual full-core JUnit run passed 114 tests with no failures, errors or skips: [verification](../../20261012-epic-legendary-cinematics/reports/post-implementation-verification.md). This is the recorded test baseline; it was not unnecessarily rerun before the new focused implementation checks.

Process-local Corretto 21.0.8 was used. This agent did not alter source, tests or configuration, start concurrent Gradle runs, commit or deploy. Local build/test evidence does not establish server/client behavior.
