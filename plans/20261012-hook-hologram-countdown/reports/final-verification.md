# Final local verification

Date: 2026-10-12. Work context: `C:/Users/hp/IdeaProjects/MineBox/EvenMoreFish`.

| Check | Final result | Evidence |
|---|---|---|
| Required first baseline package | Passed before implementation; all 1.20/1.21/26.1 variants/addons. Baseline 1.21 SHA matches preceding cinematic build. | [Baseline](baseline-verification.md) |
| Title regression red proof | Actual SDK compilation then 5 tests: four expected title-control failures, one passing invalid-hook lifecycle check. | [Report](title-regression-red.md), [log](title-regression-red.log), [XML](title-regression-red-results/) |
| Focused final Gradle JUnit | Passed: 21 tests, 0 failures/errors/skips; 9 hologram, 4 fishing processor, 3 line format, 5 native timer access; 10s. | [Log](focused-junit.log), [XML](focused-test-results/) |
| Full final core Gradle JUnit | Passed: 131 tests, 0 failures/errors/skips; 8s. Baseline was 114 passing tests; 17 new tests now pass. | [Log](full-core-junit.log), [XML](full-core-test-results/) |
| Final all-variant package | Passed core/addons/1.20/1.21/26.1; 11s, 38 tasks, 4 executed and 34 up-to-date. Packaging excludes tests; actual tests are separate above. | [Log](all-variant-build.log) |
| Final Phase9 headless regression | Passed all 10 suites; 617 Java sources parsed without syntax errors; 57 YAML resources and 18 locale diagnostics passed. | [Log](phase9-regression.log) |
| Final JAR class presence | All three standard JARs contain the hologram/countdown/native timer helpers and prior cinematic effects/budget/pattern/timeline classes. | Archive entries inspected directly. |
| Server/client acceptance | Pending under controller/operator workflow; no SFTP, server restart or client operation performed by this agent. | Local tests do not establish native runtime mappings, visual line wrapping, real privacy/lifecycle behavior or MSPT. |

## Tested behavior

The real `FishingHookHolograms` component updates the TextDisplay at the actual hook position while never calling `Player.showTitle`, `sendTitlePart`, or `clearTitle` on bite, missed transition, explicit removal, clear, offline owner or world change. Waiting counters of 100 then 50 ticks render a separate countdown line with 5.0s then 2.5s; unchanged timer text is cached. Hook invalidation removes the display and cancels the idle updater exactly once.

Native timer access tests exercise the optional modern approach API, exact legacy `timeUntilHooked` field, actual sampled `nibble` values including zero, waiting fallback, missing-mapping omission and read-only behavior. The private native fixtures remain unchanged and hook timer setters are never called. A known zero bite window followed by two updater ticks reconciles the hologram to the native waiting countdown, preventing a canceled/expired bite from displaying a false pull prompt or 0.0s window. The integration test uses wait=100 and verifies the final waiting header and 5.0s line.

The bite animation test advances two captured updater ticks for the intentional 10Hz text refresh. This adjusts test cadence while preserving the no-title assertions; the original implementation still violates those assertions after two ticks. No production code or controller-owned pure helper tests were modified by this agent.

## Commands

Process-local `JAVA_HOME=C:/Users/hp/.jdks/corretto-21.0.8` and its bin PATH were used. Gradle commands ran sequentially with no overlapping invocation:

```text
gradlew.bat --project-cache-dir qa/gradle-cache :even-more-fish-plugin:test --tests '*FishingHookHologramsTest' --tests '*HookCountdownLineTest' --tests '*NativeHookTimersTest' --tests '*FishingProcessorBiteTest' --rerun
gradlew.bat --project-cache-dir qa/gradle-cache :even-more-fish-plugin:test --rerun
gradlew.bat --project-cache-dir qa/gradle-cache build -x test
bash qa/run-phase9-regression.sh
```

The first green pass had 20 focused/130 full tests and package/smoke success. Review then added the zero-window reconciliation and wider display; all four checks above were rerun against final source with the additional integration case. Earlier passing logs remain as `pre-review-focused-junit.log`, `pre-review-full-core-junit.log`, `pre-review-all-variant-build.log`, and `pre-review-phase9-regression.log`; their package hash is provisional and superseded below.

## Final artifacts

These hashes establish local package identity, not deployment or runtime proof. Older extra variant JARs are excluded.

| Standard JAR | SHA-256 |
|---|---|
| `even-more-fish-2.3.6-SNAPSHOT-1.20.jar` | `f3df3a2f5024ef0d693327fb2e4de42c79f395dcdad96aab88db1b19503c8dbc` |
| `even-more-fish-2.3.6-SNAPSHOT-1.21.jar` | `0f21fb8eeb4140afd9467114c701e731db13ee1655ffd58f9a59965d40337271` |
| `even-more-fish-2.3.6-SNAPSHOT-26.1.jar` | `94194c0b05e89b28f2fc6b4392ed4479bb9afba06ff60da4c7e7f001950a5706` |

The Mockito/JVM class-sharing warning appears on stderr and PowerShell may wrap it as NativeCommandError despite actual Gradle exit 0; test outcomes come from actual task execution and copied XML. Initial owned-test compilation errors were fixed through real SDK consumer typing and preserved in the red report. No failure was hidden or assertion weakened.
