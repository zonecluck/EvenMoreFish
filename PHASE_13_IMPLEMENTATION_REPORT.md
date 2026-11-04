# EvenMoreFish Simulator Premium — Phase 13 Implementation Report

**Baseline**: `EvenMoreFish-Simulator-Premium-Phase12-source.zip`.

**Scope**: environment sampling, fish personality and ambient water FX.
**Status**: source implemented + headless validation; **NOT a production JAR**.

## Source changes

1. `environment/EnvironmentSnapshot.java`: immutable time/depth/weather/biome observations.
2. `environment/EnvironmentSampler.java`: max-read/tick, bounded cache, TTL, loaded-chunk read contract, invalidation, graceful failure fallback.
3. `environment/EcosystemSettings.java` and `EcosystemConfigLoader.java`: immutable `ecosystem.yml` with strict reload validation.
4. `behavior/FishPersonality.java` + `FishPersonalityService.java`: seven personalities; only idle wandering movement/flee is affected.
5. `waterfx/SurfaceFxService.java`: separate per-viewer particle budget, visibility-only fish cues, viewer fairness, owner-only lure/bite signal.
6. `AmbientPreviewService.java`: integrate with water indexing/schooling, Paper-only world sampler and particle adapter, lifecycle and diagnostics.
7. `SimulatorModule.java`: atomic config load, particle preference bridge, event-driven lure/bite visual hooks.
8. `PacketVisibilityService.java`: read-only `shownTo` guard; prevents passive fish FX before virtual entity spawn.
9. `SimulatorSubcommand.java`: new ecosystem status metrics; 18 locale messages updated.
10. `simulator/ecosystem.yml`: staging defaults, profiles, limits, comments.
11. `qa/SimulatorPremiumPhase13Smoke.java`, `qa/SimulatorPremiumPhase13ConfigSmoke.java`, validation scripts and JUnit test source.

## Key guarantees and constraints

- No new fish entities; ambient FX uses **Bukkit per-player particles** and visual-only intents.
- No mutation to `FishKey`, EMF rarity weights, bait, competition, rewards or fishing hook state.
- A reserved fish remains under `FishAiDirector` authority; school/personality cannot hijack it.
- PRIVATE fish visibility and personality disturbance respect UUID ownership and zone/world boundary.
- `WaterIndexService` remains the navigation authority. Invalid/unloaded water is skipped, not force-loaded.
- Config errors reject reload; all advanced toggles are OFF by default.
- The particle ceiling is **not the same** as PacketEvents transport budget; they are independent limits.

## Validation status

See `qa/PHASE13_VALIDATION_LOG.txt` for the exact commands and results.

- Java 21 syntax parser and pure `javac` compilation: see log.
- Smoke tests: cache & budget, depth/time/personality, privacy/visibility, failed environment read, 100-viewer particle ceiling, bad YAML candidate via API stubs.
- Regression Phase 01–12: see log.
- Full Gradle compile **not verified**: Gradle Wrapper distribution download failed (`UnknownHostException: services.gradle.org`).
- Paper runtime G3/G5/G7 and real MSPT/client rendering **not verified**.

## Next phase

Phase 14 — Rare Encounter Director: staged GOLDEN/SHADOW/GHOST appearances based on **real** EMF `FishKey`, capped cooldowns and private/shared presentation; no extra loot/double reward. After that, prioritise full Gradle compile/Paper acceptance prior to public release.
