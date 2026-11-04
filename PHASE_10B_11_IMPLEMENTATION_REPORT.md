# EvenMoreFish Simulator — Premium Phase 10B + 11 Implementation Report

> Baseline: `EvenMoreFish-Simulator-Premium-Phase10A-source.zip`.
> Scope: GUI preset selector + six-stage visual-only Premium Cinematic Director.
> Status: **SOURCE/STAGING ONLY** — no full Gradle compile, no G3/G5/G7 Paper validation.
> Gameplay/reward/competition/bait were **not** rewritten or activated.

## 1. Delivered features

### Phase 10B — HUD settings GUI

- Adds `simulator/hud-gui.yml` (`config-version: 1`, default `enabled: false`).
- `/emf hud` opens configurable inventory when GUI is enabled; otherwise keeps the Phase 10A command message.
- Buttons: `OFF`, `MINIMAL`, `BALANCED`, `CINEMATIC`, `REDUCED_MOTION`; selection calls existing async UUID preference storage and stops the current animated fish when the profile disables cinematic.
- Custom `InventoryHolder`, UUID-owned session with immutable server slot→action map; top and bottom click canceled before action, only normal left/right on a top slot accepted; shift/number/double click/drag canceled.
- Stale holder/session rechecked; `InventoryCloseEvent` only evicts the matching session; quit/reload/disable cleanup; GUI open and refresh on main thread.
- GUI title, filler, materials, lore, layout/slots, glow, sound all driven by config, MiniMessage components and strict validation; sound is resolved with `Registry.SOUNDS`.
- **This is preset selection, not yet individually configurable HUD layers**. Further independent toggle schema/GUI remains a future upgrade.

### Phase 11 — Premium cinematic

- One packet-entity owner: extended **existing** `PacketCatchCinematicService`; no parallel cinematic renderer or player-entity spawn.
- New `PremiumCinematicTimeline` (pure math) stages: `ANTICIPATION → VORTEX → SURFACE_BREAK → HERO_REVEAL → DELIVERY → FADE`.
- Preview fish is not spawned until `SURFACE_BREAK`. Common can skip most preliminary stages.
- Continuous easing and small orbit around hero pose; no camera teleport/shake, no world block changes, no invulnerability/gameplay lock.
- `simulator/cinematics.yml` has 4 presets, per-FishKey overrides, configurable stage durations, arc/orbit/trail, stage cues, bounds, separate particle emission cap.
- `BALANCED` profile uses legacy short `CatchArc` for less noise; `CINEMATIC` enables Premium V2; `OFF/MINIMAL/REDUCED_MOTION` do not play multi-stage sequences.
- Staff-only `/emf admin simulator cinematic preview <common|rare|epic|legendary>` and `... cinematic stop` (water target within 12 blocks, visual-only, never calls fish selection/rewards/history).
- On scene stage transition, local sounds and bounded particles are emitted to the viewer only. Hero reveal uses localized MiniMessage title from the real `FishKey` after result; synthetic staff preview does not pretend to be a real catch.
- Packet failure handling reuses `PacketCleanupQueue` to record partial spawn IDs and retry; cleanup has priority before movement. Supports reconnect/world-change/quit/reload/skip.
- Atomic config candidate read for `simulator.yml` + `effects.yml` + `presentation.yml` + `cinematics.yml` + `hud-gui.yml` **before** applying new Simulator settings. Invalid new config retains last accepted in-memory state.

## 2. Main file changes

```text
even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/
  SimulatorModule.java                         # reload, lifecycle, admin preview & diagnostics
  SimulatorMessages.java                       # localized title helper
  effects/
    PacketCatchCinematicService.java           # shared packet owner, old and premium scenes
    SimulatorEffectsService.java                # FX stage/trail mixer and profile quality
    PremiumCinematicTimeline.java              # NEW deterministic movement and stage timeline
    PremiumCinematicSettings.java              # NEW immutable scene profiles
    PremiumCinematicConfigLoader.java          # NEW fail-closed config loader
  presentation/gui/
    HudGuiSettings.java                        # NEW GUI settings snapshot
    HudGuiConfigLoader.java                    # NEW validation
    HudSettingsGui.java                        # NEW holder/session and click safety

even-more-fish-plugin/src/main/resources/simulator/
  cinematics.yml                               # NEW, disabled
  hud-gui.yml                                  # NEW, disabled

even-more-fish-plugin-1-21/src/main/java/com/oheers/fish/commands/
  admin/subcommand/SimulatorSubcommand.java    # staff preview/stop + metrics
  main/subcommand/HudSubcommand.java           # /emf hud GUI toggle

qa/
  SimulatorPremiumPhase11Smoke.java
  SimulatorHudGuiSmoke.java
  run-premium-phase11-smoke.sh
  run-phase11-hud-gui-smoke.sh
  run-premium-phase11-validation.sh
```

Additionally adds localized keys to 18 existing locales and a JUnit unit test for the timeline.

## 3. Safety/reward invariants

1. Scene FX and preview **never** call `EMFFishCaughtEvent`, `SimulatorCatchService`, `FishManager` roll, bait consumption, inventory, EXP, competition or DB.
2. The only real Simulator reward entry remains the previously guarded server-native event → `FishAiDirector.reelWithCommit` path.
3. Packet failure in a scene must not rewrite reward success/failure. Stage/trail exceptions are isolated and counted.
4. No new ticker task per viewer; all packet scenes use the Simulator ticker.
5. `Particle` emission cap is separate from `PacketTransport` packet count; these caps do **not** prove total outgoing packet count from all plugins.
6. Player preferences are UUID-based, already persisted async by `VisualPreferenceStore`; GUI never performs SQL or file I/O on a click.
7. Config defaults: simulator OFF, reward bridge OFF, presentation OFF, effects OFF, Premium OFF, GUI OFF.

## 4. Validation evidence

- Java parser: source syntax PASS (latest count in `qa/PHASE11_VALIDATION_LOG.txt`).
- Phase 01–10A headless regression suite: PASS.
- Phase 11 animation smoke: PASS (stages, continuity, packet cap, failure retry, preview and stop).
- Phase 10B GUI test: PASS (holder/slot, unsafe clicks, drag, stale-close, quit, disabled gate) against local API stubs.
- YAML check: 4 presets, duration bounds, all 18 locales, GUI slot uniqueness, all safety toggles OFF.
- `./gradlew ...` attempt: **FAIL before source compilation** — `UnknownHostException: services.gradle.org`, Gradle 9.5.1 unavailable.
- Actual Paper + PacketEvents video/render/GUI/runtime G3/G5/G7: **NOT TESTED**.
- The synthetic/mock tests are not substitutes for full compilation or server tests.

## 5. Remaining blockers and out-of-scope work

- **P0:** install/cache Gradle distribution; compile full core + Paper adapter; correct any discovered Java/API errors.
- **P0:** reproduce and certify native hook suppression G3, packet spawn/metadata/destroy G5, reward/bait/event/history G7 on Paper staging.
- **P1:** client review of all four preset cinematics with different ping/FOV/light/water depths and GUI anti-exploit matrix.
- **P1:** add independent per-layer toggle persistence/GUI; current Phase10B only selects 5 quality profiles.
- **P2:** schooling, environmental personality, rare encounters, custom ItemDisplay models (Phases 12–15) still not implemented.
- **P0:** do NOT use production-facing feature flags or claim Folia support.

## 6. Next work

Phase 9.2 validation remains non-negotiable. On a staging Paper server, enable only test zone and staff preview, capture packet/render evidence and `/emf admin simulator doctor`, stress test, then decide whether to continue with Phase 12 schooling and life-like movement.
