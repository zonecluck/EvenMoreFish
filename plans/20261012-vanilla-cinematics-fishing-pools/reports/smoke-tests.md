# Headless smoke verification

Date: 2026-10-12, Asia/Saigon. Worktree: `C:/Users/hp/IdeaProjects/MineBox/EvenMoreFish`.
Environment: Windows, Git Bash `C:/Program Files/Git/bin/bash.exe`, Corretto 21.0.8.9.1 (`C:/Users/hp/.jdks/corretto-21.0.8`). Commands used process-local `JAVA_HOME` and `PATH`; existing smoke scripts were not edited.

## Results

| Command | Exit | Evidence | Result |
|---|---:|---|---|
| `bash qa/run-premium-phase11-smoke.sh` | 0 | [phase11 log](../../../qa/vanilla-premium-phase11-smoke.log) | Six-stage timeline, bounded motion steps, packet budget, admin preview, partial-spawn retry and cleanup passed. |
| `bash qa/run-phase9-regression.sh` | 0 | [phase9 log](../../../qa/vanilla-phase9-regression.log) | Syntax parser: 593 Java sources, zero syntax errors; all configured offline/domain regression suites passed; 57 YAML resources and 18 locale diagnostics passed. |
| `bash qa/run-phase16-validation.sh` | 0 | [phase16 log](../../../qa/vanilla-phase16-validation.log) | Re-ran Phase9, then premium phases10–15; passed config/default/locale checks and Phase16 FIFO fairness, tick bound, retry recovery, disconnect isolation, and cleanup overflow above 4096 entities. |
| Production `RareCatchPolicy` standalone compilation and execution | 0 | [policy log](../../../qa/vanilla-rare-policy-smoke.log), [harness](../../../qa/VanillaRareCatchPolicySmoke.java) | 14 direct policy checks passed: null ID, hard Common exclusion, custom exclusion precedence, case-insensitive inclusion, rarity index threshold, finite positive weight and upper boundary. |
| `python3 qa/phase16_release_gate.py` | 2 | [release gate log](../../../qa/vanilla-phase16-release-gate.log) | Expected release block: all manifest evidence gates and human signoff remain pending. No evidence manifest fields were changed. |

The Phase16 syntax parse counted 594 sources. The controller and other workers were making permitted scoped changes during this verification; counts are recorded from each actual run. No Gradle process was launched by this worker; build and real JUnit evidence belong to the controller report.

Standalone policy command:

```bash
mkdir -p qa/out-vanilla-policy
javac --release 21 -encoding UTF-8 -d qa/out-vanilla-policy \
  even-more-fish-plugin/src/main/java/com/oheers/fish/effects/RareCatchPolicy.java \
  qa/VanillaRareCatchPolicySmoke.java
java -ea -cp qa/out-vanilla-policy VanillaRareCatchPolicySmoke
```

## Evidence limits

- Bukkit/EMF smoke stubs and synthetic packet sinks verify domain behavior under those harnesses. They do not verify actual Paper event scheduling, native hook timing, display interpolation, player privacy on a connected client, permissions/command wiring, or reward delivery on a real server.
- Existing Phase11 continuity check bounds each sampled position step to squared distance below 3. It does not measure client perceived smoothness or prove velocity continuity across every stage.
- Phase14 reports two JUnit-shaped tests with local assertion stubs; this is not a Gradle JUnit engine execution.
- Static config checks confirm Simulator, native probe, preview, AI, reward, presentation and optional Simulator effects stay OFF by default. Resource/manifest assertions are not runtime evidence and do not authorize enabling staging or production gates.
- Synthetic 1/10/30/50/100-angler timing is not Paper MSPT or a server load test.
- This worker did not execute new Fishing Pool listener/lifecycle behavior or vanilla ItemDisplay integration on Paper. Actual Paper/client G3/G5/G7/G15, load, reload/shutdown and rollback remain unverified. The release evidence manifest also still lacks controller build/JUnit attachments and human signoff.

Status: DONE_WITH_CONCERNS. All requested headless suites passed; independent release gate remains blocked as designed. Real server/client acceptance remains outstanding.
