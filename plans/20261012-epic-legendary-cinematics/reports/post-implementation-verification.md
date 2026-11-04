# Post-implementation verification

Date: 2026-10-12. Work context: `C:/Users/hp/IdeaProjects/MineBox/EvenMoreFish`. No source, configuration or tests were edited by this verification agent. Checks ran sequentially using process-local Corretto 21.0.8 and Git Bash; no concurrent Gradle invocation was started.

| Check | Result | Evidence |
|---|---|---|
| Focused actual Gradle JUnit | Passed: 13 tests, 0 failures/errors/skips. Timeline 7, style 2, particle geometry 4. Real Paper compilation passed. | [Log](focused-junit.log), [XML](focused-test-results/) |
| Full core actual Gradle JUnit | Passed: 114 tests, 0 failures/errors/skips; 13s. Compared with 106 passing baseline tests, eight additional tests pass and no baseline regression appears. | [Log](full-core-junit.log), [XML](full-core-test-results/), [baseline](baseline-verification.md) |
| Phase9 headless regression | Passed: all 10 suites; 612 Java sources parse without syntax errors; 57 YAML resources and 18 locale diagnostics. | [Log](phase9-regression.log) |
| Premium Phase11 smoke | Passed after controller corrected the isolated javac input list to include its required `Color.java` scaffold; six-stage timeline, continuity, budget, preview, retry and cleanup. Initial missing-Color compile failure remains preserved. | [Final log](premium-phase11-smoke.log), [initial failure](premium-phase11-smoke-initial.log) |
| All-variant compile/package | Passed: core/addons/1.20/1.21/26.1 variants; 1m 2s; 38 tasks, 7 executed and 31 up-to-date. Packaging excludes tests; separate actual test results are above. | [Log](all-variant-build.log) |
| Installed PacketEvents 2.13 signature/recovery harness | Passed: actual installed 2.13 transport compilation and runtime recovery after simulated visual breaker; 12s. The simulated send failure intentionally emits a SEVERE transport-disable line. | [Log](packetevents213-compatibility.log) |
| HUD GUI smoke | Passed after controller aligned two existing API scaffold methods. Covers slot-owned sessions, safe clicks, drag, stale close, quit and disabled gate; it does not load or validate source YAML. Initial harness compilation failed because `qa/stubs11` NamespacedKey lacked `minecraft(String)`; the next attempt advanced to Enchantment's missing `getByKey(NamespacedKey)`. Both APIs already passed real Gradle/Paper compilation. | [Final log](hud-gui-smoke.log), [initial failure](hud-gui-smoke-initial.log), [second failure](hud-gui-smoke-second.log) |
| JAR class presence | Passed: all three standard variant JARs contain `CinematicParticleEffects`, `CinematicParticleBudget`, `CinematicParticlePattern`, and `PremiumCinematicTimeline`. | Archive entries inspected directly. |
| Paper/client acceptance | Pending; no live server/client proof obtained. | Local checks do not establish visual quality, privacy, lifecycle behavior or actual MSPT. |

## Exact commands

All Gradle invocations use `gradlew.bat --project-cache-dir qa/gradle-cache`:

```text
:even-more-fish-plugin:test --tests '*PremiumCinematicTimelineTest' --tests '*PremiumCinematicStyleTest' --tests '*CinematicParticlePatternTest' --rerun
:even-more-fish-plugin:test --rerun
build -x test
-I qa/lake-packet-compat.init.gradle :even-more-fish-plugin-1-21:checkLakePacketRecovery
```

Smoke commands:

```text
bash qa/run-phase9-regression.sh
bash qa/run-premium-phase11-smoke.sh
bash qa/run-phase11-hud-gui-smoke.sh
```

Focused tests ran before full core tests, then Phase9 and the initial Phase11 attempt. When the Phase11 harness compilation failed, the controller was notified immediately; independent package/installed PacketEvents checks proceeded, then Phase11 was rerun after its javac input correction. The HUD harness required two narrow retries after the controller corrected existing API scaffolds; each failure was reported immediately and retained. No production change occurred after the full 114-test run/package, so passing broad suites were not unnecessarily rerun for these isolated harness fixes.

## Built artifacts

Hashes identify the standard JARs built by this run, not deployment or runtime proof. An older extra `1.21-552.jar` present in the output directory was excluded.

| Variant | SHA-256 |
|---|---|
| `even-more-fish-2.3.6-SNAPSHOT-1.20.jar` | `6418ce77677005a6976820242accaa75009d777efe98a7d0e49a6c94c99dbb86` |
| `even-more-fish-2.3.6-SNAPSHOT-1.21.jar` | `c9db5e1500fd4f80f3b87909c1697b4ab2266346f09d26c29b2f8fbd95eae64d` |
| `even-more-fish-2.3.6-SNAPSHOT-26.1.jar` | `f3f4046ee1554a7edba9665803dcf27a8302e75c7102f4bc8decb61a01bfd152` |

Mockito/JVM class-sharing and dynamic-agent warnings appear on native stderr; PowerShell may render these as NativeCommandError entries despite actual Gradle exit 0. No test, compile or smoke failure was hidden or weakened. No commit, upload or deployment was performed by this agent.
