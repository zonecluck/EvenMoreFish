# 🎣 EvenMoreFish Simulator V2 — Phase 0/1 Implementation Report

> Date: 2026-10-11  
> Source baseline: uploaded `EvenMoreFish.zip` (`even-more-fish-plugin` Java 21 core, Paper 1.21 variant).  
> Scope: **foundation and legacy bug fixes only**. This is **not** the packet-fish Simulator release.  
> Gate status: **G0/G1 PARTIAL — NOT PRODUCTION-CLEARED**.

## Implemented in this source tree

### Catch pipeline (`fishing/Processor.java`, `fishing/items/Fish.java`)
- Split `getCaughtItem` internally into `selectCatch`, `prepareCatch`, and `commitCatch` with the legacy public entrypoint retained.
- Fish size initializes before EMF catch event, while messages, potion effects, bait consumption, catch rewards, leaderboard, and metrics occur only after `fireEvent` accepts the catch.
- Fish item is built **after** the event to preserve existing addon handlers that alter fish metadata during the event.
- Caught-bait item/rewards follow a separate branch and do not generate fish statistics.
- `Fish.init()` remains public and preserves its old combined semantics for other callers. New `prepareCatch()` and `applyCatchEffects()` are available for future engine adaptation.

### Rod/bait/region (`baits/BaitHandler.java`, `fishing/items/FishManager.java`, `fishing/FishingProcessor.java`)
- Bait can affect eligible custom rods (`allow-baits: true`), respecting rod rarity and fish allow-lists, and applying compatible rod rarity/luck modifiers.
- Selection no longer emits bait-used feedback before a successful catch; consumption stays at the committed stage.
- Region boosts are **real-valued weight multipliers** with hook-based `RequirementContext` location, instead of integer-truncated rarity duplication.
- `RegionWeightMultiplier` guards non-finite/negative weights; zero multiplier disables rarity. A zero boost may change old mistakenly unmodified behavior **by design**.
- `FishingProcessor` stores cast-time hook/hand/rod fingerprint per UUID; recast/terminal states/quit/disable clean it up. It tolerates durability changes but rejects replaced rods and changed bait metadata.

### Data reporting (`database/...`, `fishing/EMFFishListener.java`)
- Catch/hunt statistics listener observes only **uncancelled** EMF events at `MONITOR`.
- Listener creates immutable `CatchStatsSnapshot`; SQL report operations are enqueued to a single bounded DB executor (8,192 queued jobs). No fresh JDBC query runs directly from this listener.
- Join preload is async; catch snapshots are captured on server thread without keeping live Bukkit objects in worker tasks.
- DB worker drains for up to 15 seconds on disable before caches and connection are closed (timeout is warned and treated as durability risk).
- `FishStats` name resolution avoids calling Bukkit offline-player API from DB worker; names are supplied from the event where known.
- `DataManager` uses revision-aware dirty tracking; flushes cannot acknowledge a newer revision than the one persisted; failed saves retain dirty keys.

## Deliberately unchanged / not yet implemented
- No PacketEvents, water index, fish agents/AI, region router, session state machine, model renderer, native hook suppression, HUD, animation or `simulator.yml` yet.
- Vanilla EMF fishing, hunting, shop, journal, competition, public `EMFFishCaughtEvent`, fish NBT schema, resources and `version: 8` legacy config remain unchanged.
- No new player-facing messages/commands, so current locale files stay untouched.
- No Folia support claim; Paper 1.21 variant remains primary target.

## Verification completed here
- ✅ Java 21 source **syntax parsing**: 330 files parsed; zero syntax errors (`JavacTask.parse`, without dependency/type resolution).
- ✅ Isolated JVM smoke: `DataManager` dirty revision update during flush, failed flush retry, fractional boosts, weighted RNG distribution, ordered DB executor/drain. Run: `qa/run-offline-smoke.sh`.
- ✅ Existing Gradle Kotlin DSL project and source resources kept; `gradlew` line endings normalized for POSIX.
- ⚠️ **Gradle `test`/`shadowJar` not executed successfully**: Gradle 9.5.1 distribution absent; network DNS cannot resolve `services.gradle.org`. The uploaded source includes remotely resolved convention plugins, so an internet-enabled build or matching Gradle caches is required.
- ⚠️ No Paper server integration tests, Paper build artifact, MSPT or packet benchmarking has been performed.

## Open issues / risks before Gate G1 can be approved

1. **Critical: event/report durability**: `EMFFishCaughtEvent` is fired before inventory delivery; an exception after the event may still enqueue a report. Async reports are not transactional across log/user/global stats. Under executor saturation or DB failure, an event report can be lost even though a fish was delivered. A future commit coordinator + durable ledger/journal must address this before claiming zero-loss stats.
2. **Thread safety outside catch path**: Other systems (shop, competition, admin operations) may still access database synchronously or mutate the same cached `UserReport`/stats from non-DB threads. Audit and centralize through repository/executor before release.
3. **Mutable cache models**: revision guard protects dirty flags, but object fields themselves are mutable and async serialization can race with other subsystems. Use immutable snapshots or exclusive ownership/versioned DTOs.
4. **Dependency type checks**: no Gradle compile in current environment; first run full `test` + `shadowJar` on a normal build machine, then resolve any compile/integration issues.
5. **Gameplay regression**: rod snapshots, bait compatibility, fractional rarity weighting intentionally alter old behavior and must be playtested against configs with bait, treasure, fishing restrictions and competition.
6. **Session API exposure**: selection/preparation/commit are private implementation boundaries so far; Phase 2–3 must move them behind shared interfaces without duplicating business logic.
7. **Missing config validation and metrics**: DB queue size/drain timeout are provisional constants in this scaffold; make configurable and instrumented before production.

## Next phase (from MASTER_IMPLEMENTATION_PLAN)

**Phase 2: simulator config, zone resolver, capability probe, and a single event router.** Keep Simulator default OFF, then **Phase 3** must prove native vanilla bite/loot suppression with no double rewards before implementing packet fish visuals/AI.

## Build/test command when network/dependencies are available

```bash
./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon
```

Then use `qa/GOLDEN_CATCH_CASES.md` as manual checklist. Do not deploy this unverified phase as the production Simulator.
