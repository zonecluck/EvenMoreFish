# 🐠 EvenMoreFish Simulator — Phase 12 Fish Schools / Admin Guide

> **Scope:** Paper staging / ADMIN-only ambient preview. Fish schooling is visual and transient; it never changes EMF fish rolls, rarity, bait, rod, loot, EXP, competition, journal or database.
>
> **WARNING:** This source still lacks a full Gradle build and Paper runtime verification G3/G5/G7. Do **not** enable Simulator/reward bridge on production until those gates pass. No Folia support claim.

## 1. Requirements and safety gates

- Start from **Premium Phase 12** (cumulative source, includes Phase 01–11).
- Paper **1.21.4+**, Java 21, compatible PacketEvents build.
- Build the desired EMF/Paper 1.21 module before staging. Confirm `:even-more-fish-plugin:compileJava`, module `test` and `:even-more-fish-plugin-1-21:shadowJar` with real dependencies.
- Keep an isolated test world/region. Snapshot configs and database before testing.
- Never set `reward-bridge.paper-gates-verified: true` without independently verifying G3/G5/G7.

## 2. Configure the preview

1. Verify a playable world and a matching region in `simulator.yml`. In a staging copy only, enable the intended zone and `simulator.enabled: true`.
2. Enable `simulator.ambient-preview.enabled: true`, leave `simulator.reward-bridge.enabled: false`, `simulator.ai-preview.enabled: false` until their runtime gates are passed.
3. Edit `plugins/EvenMoreFish/simulator/schooling.yml`:

```yaml
config-version: 1
enabled: true
limits:
  max-schools: 32
  max-schools-per-zone: 6
  max-members: 6
  decisions-per-move: 36
  neighbor-checks-per-move: 144
  recruits-per-move: 8
movement:
  decision-interval-ticks: 6
  join-radius: 6.0
  roam-radius: 12.0
  formation-gap: 1.3
  leader-speed: 0.12
  follower-speed: 0.14
  scatter-ticks: 32
  reform-ticks: 28
```

4. Reload with `/emf admin simulator reload` using `emf.admin.simulator.reload`.
5. Join using an account with `emf.admin.simulator.preview`; stand near cached surface water. Use `/emf admin simulator status` for `schooling-status` metrics.

**Default state remains OFF.** `/emf admin simulator off` is the safe fallback. The existing native-hook probe and reward bridge are NOT required to preview school motion.

## 3. Engine flow

```text
existing FishPopulationService.add(agent)
       │  (max agents / zone / private quotas stay authoritative)
       ▼
FishSchoolService.onSpawn
       ├── compatible leader nearby: attach existing agent
       ├── free school slot: create transient 1-leader school
       └── no capacity: agent falls back to existing solo wandering
       ▼
AmbientPreviewService.moveAgents (existing scheduler, once per movement interval)
       ├── school.reconcile() detaches removed/reserved/AI-driven agents
       ├── bounded school.steer(): leader chooses indexed-water waypoint
       │      └── followers select local valid water cells toward V offsets
       ├── existing agent.move(): smooth packet yaw, no extra entity
       └── existing PacketVisibilityService.sync(): destroy-first, LOD, packet caps
```

### Conditions that cannot be bypassed

- Each group contains fish with the **same world, zone, owner-visibility policy and vanilla model**.
- PRIVATE fish of two different viewers can never share a group.
- A reserved/approaching fish is detached; `FishAiDirector` continues to own bite/reel and reward decisions.
- Only indexed water cells are proposed; Paper caller validates world and loaded chunk, resolves the permitted simulator zone, and never force-loads chunks.
- Only the existing fish IDs are teleported/spawned/destroyed. Creating a school does **not** increase the entity/packet cap.
- Water invalidation, player quit, chunk unload, emergency off, reload and disable remove group references through existing population lifecycle.
- When a nearby valid bite/reel concludes, matching shared/private schools may enter `SCATTERED → REFORMING → CRUISING`, visual only.

## 4. Tuning and performance

- Keep `max-members: 4–6`, `max-schools-per-zone: 4–6` before benchmark.
- Keep `decisions-per-move ≤ neighbor-checks-per-move`, and avoid raising both in one experiment.
- Higher `formation-gap` spaces followers, but can look disorganized in narrow streams; use a flat, wide lake for first test.
- The school leader roams within a soft radius; dry blocks or region borders block targets even when the shortest route crosses them.
- The existing `PacketVisibilityService` applies proximity LOD (approx. 2/5/12-tick movement throttling by distance), and global per-tick packet budget still applies. Phase 12 does not spawn decorative entities or particles.
- `/emf admin simulator status` exposes `schools`, `members`, `decisions`, `cell checks`, `budget hits`, `scatters`.
- The counters report **source-side work**, not MSPT/TPS or client smoothness. Measure with a Paper profiler and multiple real clients.

## 5. Runtime QA gates

Use `qa/PHASE_12_PAPER_TEST_MATRIX.md` with at least 2 clients, plus a world containing:

- One open lake, one canal 2 blocks wide, one river bend, and a region boundary across water.
- Chunks that can unload during a schooling demonstration.
- Two test accounts (one with admin preview permission, one without).
- Tests using shared fish and private fish with different accounts.

Watch for jitter when fish change direction, clumps on the same water cell, unwanted fish crossing shore/regions, ghost entities after `/emf admin simulator off`, and movement starvation at distant LOD.

## 6. Failure/rollback

1. `/emf admin simulator off` (disables preview and requests virtual-entity cleanup).
2. Set `simulator/schooling.yml` → `enabled: false`, then reload after pending packet cleanup is clear.
3. If packet cleanup cannot be confirmed, disconnect affected staging clients; the existing cleanup ledger prevents silently respawning overlapping entities.
4. For production, keep `simulator.enabled: false`, `ambient-preview.enabled: false`, and `reward-bridge.enabled: false`.
5. Restore Phase 11 ZIP/source if a regression is isolated to schooling. Existing fish items/data are unchanged by Phase 12.

## 7. Not yet implemented / out of scope

- Personality/environment/weather/biome-driven behavior (Phase 13).
- Rare encounter director and unique encounter animations (Phase 14).
- Resource-pack ItemDisplay fish models (Phase 15).
- Native-hook runtime Gate G3, PacketEvents real-client Gate G5 and reward Gate G7 remain **unverified**.
