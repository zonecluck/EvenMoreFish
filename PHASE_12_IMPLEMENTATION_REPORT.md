# 🎣 EvenMoreFish Simulator — Premium Phase 12 Implementation Report

## Summary

Continues cumulatively from `EvenMoreFish-Simulator-Premium-Phase11-source.zip`.

**Scope delivered at source level:** bounded logical fish schools with leader/follower steering, a small staggered-V formation, local water-cell validation, orientation easing, scatter/reform transitions, fair rotation of groups, and safe integration into the Phase 11 packet preview. No added fish spawns/rewards/EXP and no changes to the EMF catch pipeline. Default remains disabled.

## Source changes

| File | Change |
|---|---|
| `.../simulator/school/SchoolSettings.java` | Immutable validated limits, disabled default |
| `.../simulator/school/SchoolConfigLoader.java` | Strict, atomic reload candidate from `simulator/schooling.yml` |
| `.../simulator/school/FishSchool.java` | Logical group, leader/member identity, turning/scatter/reform state |
| `.../simulator/school/FishSchoolService.java` | Spatial bucket recruitment, owner-safe school grouping, bounded steering, member reconciliation, spawn locality |
| `.../simulator/AmbientPreviewService.java` | School lifecycle, batch movement/zone gate, chunk safety, diagnostics, session disturbance |
| `.../simulator/SimulatorModule.java` | Atomic configuration integration and post-reel school disturbance |
| `.../simulator/population/FishAgent.java` | Maximum 28° packet yaw turn per movement step (position unchanged) |
| `.../SimulatorSubcommand.java` | `status` includes school metrics |
| `src/main/resources/simulator/schooling.yml` | Independent documented feature flags and steering budgets |
| `src/main/resources/locales/messages_*.yml` | School metrics key in all 18 locale files |
| `qa/run-premium-phase12-smoke.sh` + `qa/SimulatorPremiumPhase12Smoke.java` | Java 21 native headless motion, population and synthetic packet-budget tests |
| `qa/run-premium-phase12-validation.sh` | Legacy regression + Phase12 + YAML/locale safety checks |
| `src/test/java/.../school/FishSchoolServiceTest.java` | 3 JUnit tests (requires real Gradle/JUnit to run normally) |

## Architectural / safety decisions

1. `FishPopulationService` remains the *only* source of agent authority and quotas. `FishSchoolService` cannot create or consume agents.
2. A group can contain only the same `zoneId`, `worldId`, `privateOwner` and `FishVisualModel`; nearby fish with different fish keys/rarities are allowed only when their vanilla model matches.
3. Only `WANDERING` fish are moved by schooling. `RESERVED`, `INTERESTED`, `APPROACHING`, `BITE_READY`, `FLEEING`, `CAUGHT` are never steered by schools.
4. Paper runtime guards validate water index + loaded chunk + Simulator region before selecting any movement edge; no forced chunk loads and no async Bukkit API calls.
5. Neighbor/sample and decision work per movement tick is hard-capped; packet LOD/budget are inherited from Phase 09 rather than duplicated.
6. Config reload uses a separate candidate parse before changing live simulator settings, and disables all new features by default.
7. Director V2, HUD GUI and Phase 7 reward bridge remain untouched; this phase is visual-only.

## Headless validation scope

- Native Java 21 compilation of the new schooling domain classes and the executable smoke harness.
- Smoke checks for grouping, private/shared isolation, world/zone/model separation, reservation ownership, leader election, invalid-cell routing, scatter/reform, steering budget and 160-agent/100-viewer synthetic packet throughput.
- The full Phase 01–11 headless validation suite is rerun.
- Resource YAML and all 18 locales are validated.

## Known blockers / release status

- **Full Gradle compilation unavailable:** Gradle 9.5.1 wrapper cannot download from `services.gradle.org` due to DNS (`UnknownHostException`). This does not mean full compile passed.
- **Paper runtime unavailable:** packet orientation/school visual smoothness and G3/G5/G7 cannot be validated by mock tests.
- **Folia not supported/claimed** because the owner/scheduler model still targets Paper main thread.
- **No benchmark claim:** synthetic 100-viewer tests validate budgets and cleanup, not real TPS/MSPT, network throughput or client FPS.
- Schooling remains **admin-only preview**, `simulator.enabled=false`, `ambient-preview.enabled=false`, `schooling.enabled=false`, and `reward-bridge.enabled=false` in shipped defaults.

See `qa/PHASE_12_PAPER_TEST_MATRIX.md` and `docs/simulator/PHASE_12_FISH_SCHOOLING_ADMIN_GUIDE.md` for staging and rollback.
