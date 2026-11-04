# EvenMoreFish Simulator V2 — PHASE 06 IMPLEMENTATION REPORT

> Build date: 2026-10-11  
> Baseline: `EvenMoreFish-Simulator-Phase05-source.zip`  
> Delivered: **Real source modifications + headless JVM tests; Gradle/Paper runtime NOT verified.**  
> Status: **Admin-only AI preview**, **NO REWARD**, **NO production Simulator**.

## 1. Goal and non-goals

**Goal:** demonstrate that a pre-existing virtual `FishAgent` (Phase 04–05) can detect an eligible admin's settled `FishHook`, reserve exclusively, follow a route of indexed-water cells, enter a server-authoritative bite window, and react correctly to a real `REEL_IN`/`FAILED_ATTEMPT`. Both `SHARED` and `PRIVATE` agents retain their identities. The vanilla EvenMoreFish catch pipeline must be left intact for ordinary players.

**Non-goals:** fish items, loot, bait charge deductions, XP, competitions, stats, journal, custom resource-pack renderer, runtime-verified native suppression, and production deployment. G3 and G5 remain blocked until actual Paper/PacketEvents tests. G7 (reward idempotency) belongs to Phase 07.

## 2. Implementation summary

| Component | Change | Why it matters |
|---|---|---|
| `ai/FishAiDirector.java` (new) | `SEARCHING -> INTERESTED -> APPROACHING -> BITE_READY` authoritative state, `EARLY_REEL`, `MISSED_WINDOW`, `SUCCESS_PREVIEW_ONLY`, bounded candidate/path search, cooldown, TTL, ownership | One hook = one reservation; no client packet ever decides a catch |
| `ai/FishAttractionPolicy.java` (new) | Read-only EMF rarity/fish requirement evaluation, custom-rod rarity/fish filter, bait modifier attraction score, competition restriction | Reuses source catalog without mutating fish templates or consuming bait |
| `population/FishAgent.java` | AI states, token-checked transitions, controlled one-cell swim and flee/release, recenter on partial wanders | Fish visually approaches instead of instant fake RNG |
| `population/FishPopulationService.java` | TTL checks `reserved()` across all AI reserved states | Fish is not accidentally removed halfway through valid gameplay |
| `water/WaterIndexService.java` | `nearest` indexed-water target lookup with limited 3x3 loaded chunks and zone predicate | Valid hook target without chunk loading or teleport through land |
| `AmbientPreviewService.java` | AI callback executed after water scanning/agent movement, before packet syncing; `FLEEING` movement; lookup helpers | Fish motion is rendered with the existing packet visibility budget |
| `SimulatorModule.java` | Admin probe takeover integrates AI director/snapshot, reruns zone/permission/rod checks, handles real reel, abort/cleanup on quit/reload/disable | Single `FishingProcessor`; legacy gameplay preserved |
| `SimulatorSettings.java`, `SimulatorConfigLoader.java`, `simulator.yml` | 14 AI parameters with range/cross-feature constraints | Default `ai-preview.enabled: false`, explicit opt-in |
| `SimulatorMessages.java`, `messages_en/vi/vn.yml` | Localized actionbar phase/bite/outcome messages, escaped placeholders | No hardcoded player-facing Java strings |
| `SimulatorSubcommand.java` | AI counters in `/emf admin simulator status` | Diagnose active sessions/preview outcomes |

### 2.1 One reel, one outcome, zero rewards

```text
FISHING event (existing FishingProcessor)
  → PendingCast until bobber is BOBBING in water
  → EMF eligibility + Simulator zone + permissions
  → Admin probe uses NativeHookBridge.suppressBite(...)
  → Optional AI preview (requires separate permission and flags)
  → WaterIndex provides hook goal in same allowed zone
  → Shared/private agent selected based on stable FishKey
  → Atomic agent reservation by hook UUID
  → Path BFS through adjacent indexed water only
  → INTERESTED → APPROACHING → BITE_READY
  → Real reel event:
       BEFORE BITE → EARLY_REEL (release/flee)
       DURING 26 + 3 ticks → SUCCESS_PREVIEW_ONLY (consume virtual agent)
       AFTER WINDOW → MISSED_WINDOW (release/flee)
  → Session cleanup, native hook cleanup, no fish item/EXP/reward/stat updates
```

### 2.2 Region and anti-dupe boundaries

- Pathfinding only visits cached water cells in a loaded chunk; runtime `WaterNavigation.traversable(...)` rechecks that each cell belongs to the **same allowed Simulator zone**. It does not jump through a dry gap, protected zone, or different world.
- The fish's `FishKey(rarityId,fishId)` is fixed at spawn and never rerolled on bite or reel.
- `FishPopulationService.reserve(fishId, hookId, playerId)` is single-threaded/atomic under the Paper owner tick and checks `privateOwner`. A shared fish cannot be owned by two casts concurrently.
- `FishAiDirector.reel(...)` is single-consumption; repeated terminal calls return `NONE`.
- Any error, lost water, rod mismatch, region/provider change, player quit, reload or `emergencyOff` cancels the probe and releases the reservation; active AI cannot continue in stale config.
- `FLEEING` animation has one adjacent-water target and returns to `WANDERING` afterwards; no trapped fish state.

### 2.3 Preview attraction vs real EMF catch logic

- The AI evaluates EMF rarity permissions/requirements, fish requirements, rod whitelist, region weight zero, competition-only policy and one applied bait's modifiers. `BaitNBTManager.randomBaitApplication()` is sampled **once per cast**; bait is never decremented.
- Attraction score influences **which existing fish swims first**, not whether a new rarity is rolled. This is a preview policy, not a one-to-one recreation of the entire EMF RNG (pre-decided rarity, reward table, competing bonus sources, loot odds). Full canonical reconciliation is required in Phase 07.
- No `Fish.prepareCatch`, `Fish.give`, `EMFFishCaughtEvent`, leaderboard or database write exists in this preview path. Even a successful reel is only `SUCCESS_PREVIEW_ONLY`.

## 3. Configuration and test-drive (STAGING SERVER ONLY)

Requires successfully compiled Paper 1.21 variant + PacketEvents compatible with the existing Phase 05 adapter. **Do not run on production.** The baseline compile and G3/G5 live validation are still missing.

```yaml
# plugins/EvenMoreFish/simulator.yml
simulator:
  enabled: true
  dependency:
    on-unavailable: LEGACY_EMF
  ambient-preview:
    enabled: true
  native-hook-probe:
    enabled: true
    max-active: 8
    session-timeout-ticks: 600
  ai-preview:
    enabled: true
    reaction-window-ticks: 26
    grace-ticks: 3
    max-wait-ticks: 500
zones:
  central_lake:
    enabled: true
    type: CUBOID
    world: world
    min: {x: -120, y: 55, z: 220}
    max: {x: 120, y: 115, z: 460}
```

Only test accounts with **both** `emf.admin.simulator.probe` and `emf.admin.simulator.preview` should see and catch the visual agents. The normal player must have **neither** permission and must still receive regular EMF rewards. Adjust world and coordinates to the test server. Reload with `/emf admin simulator reload`; run `/emf admin simulator status` and `/emf admin simulator trace` to inspect the AI.

`ai-preview.enabled` is `false` by default. It is rejected by the config validator unless `native-hook-probe.enabled` and `ambient-preview.enabled` are both true. It also checks `ai.max-active <= probe.max-active` and `ai.max-wait-ticks <= probe.session-timeout-ticks`.

## 4. Validation actually executed

| Check | Result | Scope |
|---|---|---|
| Java 21 syntax parse | PASS, 444 files | Syntax only, **not** type-checked against Bukkit/Paper |
| Phase 01 headless smoke | PASS | DB revision/race, RNG, persistence worker |
| Phase 02–03 headless smoke | PASS | Zone arbitration, immutable config, probe/offhand |
| Phase 04–05 headless smoke | PASS | Shared/private fish, packet/viewer budget and mock transport |
| **Phase 06 headless smoke** | **PASS** | Actual production AI domain: pathfinding, zone exclusion, two-owner reservation, private owner, bite, early/missed/repeated reel, TTL reserved, cleanup |
| YAML parsing | PASS | Simulator + EN/VI/VN locale YAML structurally valid |
| Full Gradle `test`, `shadowJar` | **BLOCKED** | Wrapper cannot download Gradle 9.5.1 (`UnknownHostException`); no resolved external compilation |
| Paper/PacketEvents live integration | **NOT TESTED** | Requires real Minecraft client, Paper server, plugin dependencies |
| Folia support | **NOT SUPPORTED** | Global ticker/zone/world operations not region-owned |

Run locally:

```bash
./qa/run-parse-only.sh
./qa/run-offline-smoke.sh
./qa/run-simulator-phase23-smoke.sh
./qa/run-simulator-phase45-smoke.sh
./qa/run-simulator-phase6-smoke.sh
./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon
```

## 5. Unresolved hazards / blockers

1. **G3 native loot suppression**: Probe code is experimental. Must observe actual `PlayerFishEvent` order on Paper 1.21.4 and later, with main/offhand, Lure, anti-cheat, WorldGuard and unusual cancellation. The preview does not constitute G3 proof.
2. **G5 PacketEvents live rendering**: Spawn/metadata/teleport/destroy packets are not compiled against dependency or observed in the client in this environment. Expect potential adapter changes at actual build/runtime.
3. **EMF statistics/competition**: Phase 06 never commits rewards by design. Phase 07 must revalidate selected `FishKey` at transaction time and commit exactly once, respecting bait/rod/treasure outcomes and cancellation.
4. **Visual validation**: Pathfinding runs over indexed surface-water cells. Complex multilevel water structures and water-flow changes require Paper integration tests, possibly a richer water mesh in future.
5. **Performance**: All limits are configured budgets, not TPS/MSPT metrics. External requirement hooks and zone lookups may be expensive. Use spark profiler at 30–100 viewers on a test server.
6. **Missing dependencies**: With PacketEvents absent, ambient preview and AI preview do not start; legacy EMF catches continue under `on-unavailable: LEGACY_EMF`.

## 6. Acceptance gate status

- **G4 (domain model):** headless coverage; live chunk lifecycle still pending.
- **G5 (packet visual):** still pending live client test.
- **G6 (AI gameplay):** coded and verified headless; **NOT formally passed** until actual Paper client demonstrates swim → bite → real reel and no ghost/cancel errors.
- **G3 (native suppression):** BLOCKING before any production Simulator behavior.
- **G7 (reward commit):** NOT STARTED. Must be complete before releasing to normal players.

## 7. Next phase (Phase 07)

1. Complete G3/G5/G6 integration on a real Paper test server.
2. Design `SimulatorCatchCommit` using fixed `FishKey`; revalidate location, zone, rod, fish permissions/requirements and fishing session identity at commit time.
3. Preserve vanilla treasure/bait loot rules (including fallback), `EMFFishCaughtEvent`, competition, rewards, journal, stats and inventory overflow.
4. Use one idempotent transaction with explicit claim state. Do not grant multiple items for repeated reel events or cross-reload callbacks.
5. Add cancellation and rollback paths, stress/playtest, then only enable the production Simulator mode if every release gate passes.
