# 🎣 EvenMoreFish Simulator V2 — Phase 2–3 Implementation Report

**Date:** 2026-10-11  
**Baseline:** `EvenMoreFish-Simulator-Phase01-source.zip`  
**Target:** Paper 1.21.4+, Java 21; module `even-more-fish-plugin-1-21`  
**Scope:** Zone configuration + event routing + admin-only native hook suppression probe; **NO production Simulator**, no packet fish, no rewards.  
**Gate G2:** Static/offline tests partially passed; full compile/Paper tests pending.  
**Gate G3:** Native event ownership probe implemented but **runtime proof is pending**. Do NOT enable it for players.

## What was actually implemented

### 1. Independent `simulator.yml` config
- `config-version: 1`, `simulator.enabled: false` (default), policy, native probe controls and named zones.
- `SimulatorConfigLoader` loads with `YamlConfiguration.load(...)` (malformed YAML throws), validates numeric limits/worlds/types/bounds/IDs and atomically swaps immutable snapshots; invalid reload retains last good snapshot.
- Keeps legacy EMF `config.yml` `version: 8`, legacy `messages.yml` and fish/catalog/economy schemas unchanged.
- Invalid world references are rejected only for **enabled** zones; bundled disabled examples can use worlds not yet created.

### 2. Zone resolution (one control plane, not another fishing listener)
- `WORLD`, `CUBOID`, `WORLDGUARD_REGION`, `REDPROTECT_REGION` membership; external provider classes are isolated in adapters.
- Matching `deny-simulator: true` overrides positive zones; otherwise highest priority, then ascending ID.
- Provider absent/query failure returns **no zone**, never implicitly creates a server-wide zone.
- Zone membership does not grant permission to fish. The probe additionally checks EMF world/region restrictions, player toggle, rod and competition rules.
- No scan of unloaded chunks, no global per-tick entity query. One bounded polling task runs **only** with explicit enabled probe or `DENY` policy.

### 3. Single fishing event owner + pending hook settlement
- Existing `FishingProcessor` is still **the only** EMF `PlayerFishEvent` catch listener.
- At `FISHING`, record a **bounded pending cast** (max 64), but **legacy EMF** remains owner until the hook has landed and its *water coordinates* match a Simulator zone.
- Only admin players with `emf.admin.simulator.probe`, `simulator.enabled: true`, `native-hook-probe.enabled: true`, valid zone and a supported 1.21 native adapter can enter a probe session.
- When ownership transfers, remove legacy waiting hologram and cast fingerprint to avoid parallel processing.
- For any non-admin or out-of-zone cast, the existing EMF catch path is unchanged.

### 4. Native hook control and guard (diagnostic, unproven)
- `NativeHookBridge` interface in core, `Paper121NativeHookBridge` implementation isolated in the Paper 1.21 variant.
- Try postponing native bite via `FishHook#setWaitTime(min,max)`/`setWaitTime(int)` and verify `getWaitTime`; **does not** use deprecated/non-functional `setBiteChance(0)`.
- During probe `BITE` is unexpected -> cancel the event, tear down probe and remove the hook.
- During probe `CAUGHT_FISH` -> cancel, zero EXP, remove caught Item if already valid, close session. Guard late events with short bounded tombstones.
- During probe `REEL_IN` / `FAILED_ATTEMPT` -> cleanup without granting rewards. `CAUGHT_ENTITY` preserves entity-hook semantics.
- Probe never invokes `Processor.getCaughtItem`, `EMFFishCaughtEvent` or reward pipeline.
- Probe sessions store UUID, hook UUID, cast hand and cloned rod fingerprint (handles OFF_HAND), zone ID, config revision, state and age; cleanup on quit, disable, reload, timeout, rod swap, moved-out zone, permission loss and native bridge failure.
- 128-entry bounded debug trace for the actual Paper event order; `/emf admin simulator trace` shows last 15 events.

### 5. Paper 1.21 Brigadier admin commands
- `/emf admin simulator` — help
- `/emf admin simulator status` — readiness, capability and counters
- `/emf admin simulator trace` — last native probe event transitions
- `/emf admin simulator zone list [page]` — paginated 12 per page
- `/emf admin simulator zone info <id>` — named zone
- `/emf admin simulator here` — player position/zone, player-only
- `/emf admin simulator reload` — atomically validate/reload simulator.yml only
- `/emf admin simulator off|on` — emergency routing switch (NOT a production enable flag)

Admin nodes default OP and are dynamically registered because this project uses generated plugin descriptors. Player permission `emf.simulator.use` defaults FALSE when required.

Simulator text is sourced from EMF's bundled `locales/messages_en.yml`, `messages_vi.yml`, `messages_vn.yml`, with `messages.yml` overrides and default fallback, not Java hardcoded sendMessage strings.

## Mode and safety semantics

| Configuration | Actual behavior |
|---|---|
| `simulator.enabled: false` (default) | 100% legacy EvenMoreFish. No probe tick task. |
| enabled: true, native probe: false, `on-unavailable: LEGACY_EMF` | 100% legacy EvenMoreFish; status/here commands still inspect zones. |
| enabled: true, native probe: true, admin permission present | Admin-only native hook probe in an enabled zone; **NO fish/EXP reward**. |
| enabled: true, native probe: true, regular player | Legacy EvenMoreFish, unless explicit `DENY` fallback is configured. |
| enabled: true, `on-unavailable: DENY` | When unavailable, an eligible hook landing in an enabled Simulator zone is removed. **Use only for controlled testing.** |
| missing WorldGuard / RedProtect for corresponding zone | Zone never matches (fail closed). |
| invalid `simulator.yml` on reload | Keep old valid snapshot and existing probe sessions. |

## Source changes

**Core added:**
- `config/simulator/SimulatorSettings.java`, `SimulatorConfigLoader.java`
- `fishing/simulator/SimulatorModule.java`, `SimulatorMessages.java`
- `fishing/simulator/zones/{SimulatorZoneResolver,ZoneSelection,WorldGuardZoneAdapter,RedProtectZoneAdapter}.java`
- `fishing/simulator/engine/{EngineMode,NativeHookBridge,ProbeSession}.java`

**Versioned Paper 1.21 added:**
- `Paper121NativeHookBridge.java`
- `commands/admin/subcommand/SimulatorSubcommand.java`

**Modified:** `EvenMoreFish.java`, `FishingProcessor.java`, `EMFModule.java`, Paper 1.21 `AdminCommand.java`.

**Resources:** new `simulator.yml`, extended EN/VN/VI locale sections. Other locale files continue to use regular message fallback if default locale provides the new keys.

## Tests performed here

- ✅ Java 21 parser parsed **428 Java source files** with 0 syntax errors (syntax only, not type-check/compile).
- ✅ Headless simulation: overlap/priority/deny, disabled-zone policy, cuboid boundaries, immutable zone snapshot.
- ✅ Headless simulation: offhand cast and fingerprint copy, one-time bite transition, idempotent cleanup.
- ✅ Re-ran Phase 01 smoke (dirty flush revision, DB executor, weighted RNG, fractional rarity boost).
- ✅ Python YAML parser accepted `simulator.yml` and EN/VN/VI locale files after resolving legacy `help-admin` list/map mismatch.
- ❌ Gradle `test` and `shadowJar` **NOT RUN**: wrapper attempted to fetch Gradle 9.5.1 but failed DNS (`UnknownHostException: services.gradle.org`). See `qa/PHASE23_GRADLE_BUILD.log`.
- ⚠️ No runtime verification on Paper 1.21.4/1.21.11; event ordering, native loot cancellation and compatibility with protection plugins are still unproven.

## Gate G3 — mandatory Paper integration test before Phase 4/5 deployment

See `qa/PHASE_03_PAPER_TEST_MATRIX.md`. Test on an isolated Paper server with cloned configs, survival operator and regular player accounts, optional WorldGuard/RedProtect, lure rods and OFF_HAND.

**Do not claim G3 green from parse-only/headless tests**. If unexpected native catch/EXP, non-owner reward, region bypass, or cancellation oddities occur: leave `simulator.enabled: false`, fix bridge/router, retest.

## Open risks (carry to next milestone)
1. 1.21 native wait API and event cancellation must be observed in a running server with real hooks. `BITE` event cancellation alone must never be trusted as loot suppression.
2. Late plugins can mutate events at equal/HIGHEST priority; a real compatibility matrix is needed.
3. WorldGuard/RedProtect zone containment is not the same as a comprehensive protection flag evaluator. Server protection cancellation and EMF allow-list checks still apply.
4. Phase 01 existing DB/legacy reward durability issues are not solved by this routing scaffold.
5. No renderer, FishAgent, private/shared fish, minigame, cache quotas, content balancing or resource-pack asset code exists yet.
6. Folia region-thread support is not claimed. Paper-only runtime API is used in this phase.
7. The old `config.yml`/`messages.yml` migration conventions must be tested on an existing installation, not just new install.

## How to build on a connected workstation

```bash
./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon
```

Run static/offline checks without Gradle:

```bash
./qa/run-parse-only.sh
./qa/run-simulator-phase23-smoke.sh
./qa/run-offline-smoke.sh
```

**Next technical step:** Complete runtime Gate G3, then Phase 4 water index + fish population identities before spawning packet visuals (Phase 5). Keep current EMF fallback at all times.
