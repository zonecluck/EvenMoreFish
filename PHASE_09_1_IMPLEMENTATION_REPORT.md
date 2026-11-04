# 🎣 EvenMoreFish Simulator V2 — Phase 9.1 Implementation Report

**Date:** 2026-10-11  
**Baseline:** `EvenMoreFish-Simulator-Phase09-source.zip`  
**Status:** source-level hardening + headless validations, **NOT a Paper runtime-certified release**.

## 1. Changed code

| Component | Change | Reason |
| --- | --- | --- |
| `PacketCleanupQueue` (new, core) | Bounded, idempotent virtual-entity destroy retry ledger per viewer/entity ID; failures stay pending; explicit disconnect forget | A failed `spawn()` may already have reached client before metadata failed |
| `PacketTransport` | Clarified partial-spawn semantics in contract comment | `false` never means packet definitely was not sent |
| `PacketVisibilityService` | Tracks unconfirmed spawns, prioritizes retry before new traffic, refuses new spawns when cleanup not confirmed; catches transport spawn/teleport exceptions; counts packet budget | Prevent silently untracked ghost fish in packet failure scenarios |
| `AmbientPreviewService` | Retains outstanding cleanup ledger across stop/reload; retries it; refuses preview restart while unresolved; viewer logout/dimension changes clear stale client IDs | Do not lose cleanup history during PacketEvents outages |
| `PacketCatchCinematicService` | Same partial-spawn/destroy retry behavior, bounded queue, prevents duplicate viewer cinematic until cleanup; catches packet exceptions | Cinematic fish are also packet entities and need lifecycle recovery |
| `SimulatorEffectsService`, `SimulatorModule`, command | Exposes outstanding cinematic destroy count in admin status | Observability: `effects-status` previously showed failures but not retained IDs |
| `SingleSlotDeliveryPlanner` (new, core) | Clones a whole-stack single-slot inventory insertion plan | Avoid partially adding fish through multiple slots then failing when dropping leftovers |
| `SimulatorCatchService` | Uses one `PlayerInventory#setItem` write with readback verification OR a whole-stack ground drop; never mixes partial addItem + leftover drop | Reduce ambiguous partial item delivery; do not retry after an uncertain write |
| 18 locale files | Added `{pending}` to the existing effects diagnostics line | Keep user-visible text in locale YAML, including VI/VN |
| QA scripts and tests | Extended Phase 8/9 regression; added Phase 9.1 independent smoke and JUnit tests | Partial packet failure, retries, slot insertion plans |

`SingleSlotDeliveryPlanner` is deliberately conservative: if the whole catch does not fit into ONE ordinary storage slot, it follows the configured ground-drop fallback. No armor/offhand slots are used. This is not a formal transaction: another plugin can interfere, a world item spawn can be cancelled, and server crashes are outside this in-memory protection model.

## 2. Validations actually executed

- Java 21 `JavacTask.parse()`: **455** Java source files, **0 syntax errors** (NOT a typechecked whole-project compilation).
- **49** YAML resource files parsed with PyYAML; all **18** locale files contain the new `{pending}` placeholder.
- Headless `qa/run-offline-smoke.sh`, `run-simulator-phase23-smoke.sh`, `run-simulator-phase45-smoke.sh`, `run-simulator-phase6-smoke.sh`, `run-simulator-phase7-smoke.sh`, `run-simulator-phase7-stats-smoke.sh` passed.
- Phase 8 smoke passed (including injected partial cinematic spawn and failed destroy retry).
- Phase 9 smoke passed (including injected partial fish spawn, thrown teleport, failed cleanup and mock 1/10/30/50/100-viewer load).
- Phase 9.1 smoke passed (single-slot delivery plans, orphan queue capacity/deduplication/recovery).
- Reviewed public PacketEvents **v2.14.0** wrapper constructor declarations and Paper **1.21.11** `PlayerInventory` API Javadocs. This is an API cross-check only.
- Tried `./gradlew --version`: **FAILED before Gradle runs**: `UnknownHostException: services.gradle.org`; Gradle 9.5.1 distribution absent. No Gradle/JUnit task or Paper server test was executed. See `qa/PHASE091_GRADLE_BOOTSTRAP.log`.

The combined regression script was initially missing a new javac input (`PacketCleanupQueue` in the Phase 4–5 script); this was fixed. A longer run was interrupted before completing Phase 8; subsequent Phase 8/9/9.1 scripts were executed independently and all passed. Do not interpret the interrupted run as a full combined-script PASS.

## 3. Known residual risks and release blockers

1. **G3 not passed:** Native Paper `FishHook` suppression and event ordering must be tested in game. Native `CAUGHT_FISH`, loot and EXP must not be duplicated.
2. **G5 not passed:** Packet fish visually spawn, move and despawn correctly on Java clients / PacketEvents live server remains unverified. If the packet backend becomes permanently unavailable, retry cannot deliver; reconnect may be needed to clear ghosts, and restarting the plugin/server is required to recreate a tripped adapter.
3. **G7 not passed:** EMF fish identity/size/metadata, `EMFFishCaughtEvent`, bait, rod, competition, inventory and persistent history must be verified on live Paper. Post-event addon side effects are not transactionally reversible.
4. **Item delivery cannot be fully atomic:** `setItem` readback or `dropItem` can still have ambiguous outcomes under server crashes, illegal off-thread inventory mutations, exceptional inventory providers or cancelled entity spawn. No retry is attempted after uncertain delivery.
5. **Gradle blocked by DNS:** Java parser and isolated JVM smoke cannot establish type compatibility with Paper/PacketEvents/third-party API jars.
6. **Admin preview only:** Player-facing Simulator rollout is NOT implemented; reward bridge defaults OFF and requires explicit staged sign-off.
7. **No Folia claim:** Scheduler and shared-world access remain Paper owner-thread only.

## 4. Safety defaults and rollout decision

- `simulator.enabled` remains false by default.
- `reward-bridge.enabled` and `paper-gates-verified` remain false by default.
- No new public Simulator command / progression / database schema migration.
- Legacy EvenMoreFish fishing remains the gameplay fallback.
- **Release decision: NOT READY.** Do not distribute a JAR or enable player reward flows until G3/G5/G7 and full Gradle test/build are independently verified.

Next: on a machine with network/dependency cache, run Gradle build and `qa/PHASE_091_PAPER_TEST_MATRIX.md` against a clean staging server.
