# Baseline verification

Date: 2026-10-12. Work context: `C:/Users/hp/IdeaProjects/MineBox/EvenMoreFish`.

Checks ran sequentially before cinematic implementation validation. No source, configuration, or tests were edited by this verification agent. Process-local `JAVA_HOME=C:/Users/hp/.jdks/corretto-21.0.8` and its `bin` PATH were used; smoke ran through `C:/Program Files/Git/bin/bash.exe`.

| Check | Command | Result | Evidence |
|---|---|---|---|
| Required first all-variant package | `gradlew.bat --project-cache-dir qa/gradle-cache build -x test` | Exit 0; BUILD SUCCESSFUL in 2m 40s; 38 tasks, 2 executed and 36 up-to-date. Core, addons and 1.20/1.21/26.1 variants passed. | [Log](baseline-build.log) |
| Full core JUnit | `gradlew.bat --project-cache-dir qa/gradle-cache :even-more-fish-plugin:test --rerun` | Exit 0; 106 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL in 29s. | [Log](baseline-core-junit.log); [XML directory](baseline-core-test-results/) |
| Phase9 regression | `bash qa/run-phase9-regression.sh` | Exit 0; all 10 headless suites passed; 606 Java sources parsed with no syntax errors; 57 YAML resources and 18 locale diagnostics passed. | [Log](baseline-phase9.log) |

The initial core JUnit invocation without `--rerun` returned UP-TO-DATE, so it was repeated with actual test-task execution. The reported counts come from the copied result XML, not the cache-only invocation.

The earlier 89-test report recorded two `SingleSlotDeliveryPlannerTest` failures caused by null `Bukkit.server`. Those failures did not reproduce in this baseline: both cases passed. The current existing harness uses a private `PlainStack` subclass that overrides `isSimilar` for material-only comparison without a server ItemFactory; this agent did not introduce or alter that harness.

The JVM class-sharing warning in the JUnit log is emitted by Mockito's appended bootstrap classpath and did not fail the test task. PowerShell wraps that native stderr line as `NativeCommandError`; the captured process exit remains 0.

These checks establish local baseline package/test behavior. Synthetic packet benchmarks and isolated Java smoke do not establish Paper/client cinematic rendering, privacy, cleanup, gameplay, or production acceptance. No commit, upload, deployment, or runtime acceptance was performed.
