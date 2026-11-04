# EvenMoreFish Simulator V2 — Premium Upgrade / Phase 10A

> **Source milestone:** Phase 9.1 → Phase 10A Presentation Foundation.  
> **Target:** Paper 1.21.4+ / Java 21 (core module still compiles against EMF's legacy Paper API).  
> **Status:** source implemented and headless regression validated; **not a production JAR**, **not a Paper runtime certification**.  
> **Source of requirements:** `MASTER_UPGRADE_PLAN_EvenMoreFish_Premium_Fishing_Experience.md` §3, §9, §10 and §14.

## 1. Product change

Legacy Simulator had **two competing ActionBar writers**:

- `SimulatorModule.aiFeedback.phase()/finished()` sent a direct `SimulatorMessages.actionBar(...)` notification.
- `SimulatorEffectsService.tick()` sent the countdown as another direct ActionBar update.

During `BITE_READY`, both could write the ActionBar in the same server tick. This is a verified internal UI-contention issue. **The screenshots may also show world-space TextDisplay/hologram overlap; that visual source is not conclusively identified without client/runtime inspection.** Phase 10A creates a single Simulator ActionBar writer and preserves the existing legacy EMF catch and reward engine.

## 2. Implemented features

### ActionBar ownership, ordering and cleanup

- `presentation/PresentationArbiter`: pure Java owner-thread arbitration; one intent per `(UUID, channel)` with priority, expiry, dedupe and tick throttle. Old `hookId/sessionId` cannot write into a new cast.
- Priority: `CATCH_RESULT > BITE_READY > FAILED_REEL > ENCOUNTER > APPROACHING > AMBIENT`.
- `BITE_READY` may preempt a lower-priority hint immediately. `CATCH_RESULT` remains visible after the native hook/session closes. No obsolete hint may overwrite it.
- No new repeating task per player: central Simulator ticker drives `FishingPresentationService.tick()`.
- `INTERESTED` no longer occupies the central HUD by default. Only state-based cues/world-space effects are used unless admin explicitly configures the hint.
- For a bite, the ActionBar shows **one combined progress bar + remaining seconds + reel instruction**. It is throttled to the configured interval.
- All player-facing text is loaded from existing EMF locale resources, not hardcoded in Java.

### User preference presets

`OFF`, `MINIMAL`, `BALANCED`, `CINEMATIC`, `REDUCED_MOTION` are implemented in `UiProfile`. `REDUCED_MOTION`, `MINIMAL` and `OFF` stop an ongoing packet cinematic when selected. OFF suppresses this Simulator UI (not gameplay or other plugins' UI).

Command added on the **existing** Paper 1.21 root:

```text
/emf hud
/emf hud off
/emf hud minimal
/emf hud balanced
/emf hud cinematic
/emf hud reduced-motion
```

Required permission: `emf.simulator.preferences` (registered with default `TRUE`). Console receives the existing locale player-only message. The old `/emf admin simulator effects on|off` remains admin-only and now also mutes/unmutes presentation for the current session.

### Async preference persistence

- File path: `plugins/EvenMoreFish/simulator/player-prefs/<UUID>.yml`.
- Minimal explicit YAML schema: `config-version: 1`, `profile: ...`.
- Lazy UUID prefetch on join only while Simulator is enabled, otherwise on first use.
- Single owner-managed, bounded queue worker handles file I/O and atomic replace.
- In-memory edits have priority over late async load results; quit/new join cannot receive a stale callback.
- Plugin disable uses a bounded flush (`3 seconds`), logs timeout and never silently declares persistence succeeded.
- Important: the confirmation is for the in-memory setting; disk failures are logged as warnings and counted in diagnostics.

### Config / diagnostics / localization

- New file: `even-more-fish-plugin/src/main/resources/simulator/presentation.yml` (strict versioned validation and snapshot fallback). **`enabled: false` by default**.
- New localized keys in all 18 EMF locale files: `presentation-bite`, `presentation-current`, `presentation-changed`, `presentation-status`.
- `/emf admin simulator status` reports HUD emitted intents, stale/priority drops and asynchronous preference I/O failures.
- Legacy `config.yml` version and all rarity/fish/rod/bait/competition APIs are unchanged.

## 3. Code integration

- `SimulatorModule`: parses candidate presentation.yml before swapping snapshots; starts a session on successful `aiDirector.begin`; routes phase, countdown and terminal result to the presentation owner; closes/cleans stale sessions on cast, quit, reload and shutdown.
- `SimulatorEffectsService`: no longer sends ActionBar directly. It forwards countdown updates to the owner and respects profile sound/particle/cinematic flags.
- `FishingProcessor`: async pref prefetch on join (only when preview engine is enabled).
- `MainCommand`: adds the player-facing `hud` branch.
- `SimulatorSubcommand`: diagnostics.
- `VisualPreferenceStore`: async disk work; does not use Bukkit world/entity methods.

## 4. Verification evidence

**Executed on this source snapshot:**

1. `bash qa/run-phase9-regression.sh`: Phase 01→9.1 headless smoke tests PASS; synthetic packet budgets remain within test caps.
2. `bash qa/run-premium-phase10-smoke.sh`: Java 21 real compile + assertions for arbitration, priority, expiry, stale cast, persistence and ordered writes PASS.
3. `bash qa/run-premium-service-smoke.sh`: Real `FishingPresentationService` compiled and exercised with deliberately minimal Bukkit/EMF stubs; state transition → single ActionBar → persisted preference PASS.
4. Java 21 `JavacTask.parse()` for **463 source Java files**: 0 syntax errors.
5. PyYAML parse for **50 YAML resource files**, **18 locales**, and production-safe feature flags OFF: PASS.

**NOT VERIFIED:**

- Full Gradle task `:even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar` fails **before compile** with `UnknownHostException: services.gradle.org` downloading Gradle 9.5.1 (`qa/PREMIUM_GRADLE_VALIDATION.log`). This is an environment/dependency availability blocker; static parsing cannot prove full type/API compatibility.
- Paper staging / client: G3 native hook, G5 PacketEvents visual rendering and G7 reward integration remain unverified.
- 50–100 player real load / timings and server TPS/MSPT have not been measured.

## 5. Still pending in the approved premium upgrade plan

- **Phase 9.2 gate remains blocked** until full Gradle compile and real Paper evidence is available.
- **Phase 10B:** fully configurable session-safe HUD settings GUI, individual toggles for each channel, optional BossBar and cross-plugin HUD conflict tests. Phase 10A has preset commands, **not the GUI**.
- **Phase 11:** Premium multi-stage `CinematicDirector` with keyframes, choreography, rarity scene timeline and per-fish overrides. **Not implemented in this delivery**; Phase 8 packet arc remains unchanged.
- Phase 12–16: fish schools, environment FX, rare encounter, custom model and rollout.

## 6. Staging instructions

1. Make a backup; **do not replace production EMF** with this source ZIP.
2. On a machine with Gradle distributions/dependency cache, run:

   ```sh
   ./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon
   ```

3. Create an isolated Paper 1.21.4+ server and install the compatible external PacketEvents build.
4. Test default config with **all Simulator flags OFF**, then enable preview only in a disposable test world/zone with admin probe permissions.
5. When testing HUD, set `simulator/presentation.yml: enabled: true`, enable approved Simulator admin preview, and exercise `/emf hud` presets and session transitions.
6. Follow `qa/PHASE_10_PAPER_TEST_MATRIX.md`, record client video, console, server timings and any plugin conflicts. Never set `reward-bridge.paper-gates-verified: true` just to unlock tests.
7. If a test fails, disable preview (`/emf admin simulator off`), restore the previous plugin JAR, and retain the error logs.
