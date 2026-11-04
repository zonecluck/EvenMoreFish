# 🎣 EvenMoreFish Simulator — Phase 13 Ecosystem Admin Guide

## Scope and safety

Phase 13 extends the **admin-only ambient packet fish preview**, not the live catch rules:

- `EnvironmentSampler`: depth band, biome, day/night, weather (cached; loaded chunks only).
- `FishPersonalityService`: subtle WANDERING speed profiles, DEEP_DWELLER depth preference, SHY flee on disturbance.
- `SurfaceFxService`: viewer-local bubble/ripple/lure/bite cues, strictly budgeted.
- No extra entity/loot/spawn roll; `FishAiDirector` owns reserved/approaching fish.
- No Folia support claim. World/particle APIs are used **only on the Paper main owner thread**.
- These mechanics **do not** change EMF rarity odds, bait strength, native fishing rewards, or competition results.

## Files and enable sequence

New file: `plugins/EvenMoreFish/simulator/ecosystem.yml` (bundled and copied if absent).

1. Back up plugin data and use a private **Paper staging** server with the required PacketEvents adapter.
2. Keep `simulator.yml`'s `reward-bridge.enabled: false`, and keep its `paper-gates-verified: false` unless independently verified on Paper.
3. Configure your preview `WORLD` / `CUBOID` / external region and enable the **existing admin preview** following the Phase 12 guide.
4. Set `ecosystem.yml: enabled: true`; toggle the three features individually:
   - `environment.enabled`: cached reads of biome/weather/day/depth.
   - `personality.enabled`: ambient movement profiles and SHY escape from a disturbance.
   - `water-fx.enabled`: cosmetic owner/viewer-specific particles; starts **OFF**.
5. Reload using `/emf admin simulator reload`; examine `/emf admin simulator status`.
6. Confirm custom `FishKey` overrides using exact case-sensitive `rarityId:fishId` values.

`ecosystem.yml` default is `enabled: false`, and Water FX also defaults to `false`.
Any malformed value rejects the **whole candidate reload**, leaving the previous valid settings active.

## Personality reference

| Profile | Ambient behavior | Does not do |
|---|---|---|
| `CALM` | Slower cruising | Does not nerf rarity |
| `CURIOUS` | Slightly livelier wandering | Does not cause fake bites |
| `SHY` | Flee one valid water cell on disturbance | Never breaks reservation |
| `AGGRESSIVE` | Faster ambient swim | Does not improve hooking chance |
| `NOCTURNAL` | Faster at night, slower by day | Does not force nocturnal spawns |
| `DEEP_DWELLER` | Prefers downward valid water neighbor | Never force-loads chunks |
| `MYTHIC` | Gentle slow motion with optional ripple | Does not add legendary loot |

Environmental data is **coarse** (chunk + 4-block Y bands) and cached for 200 ticks by default; adjacent biomes within the same chunk may share a short-lived snapshot. This is appropriate for ambient visuals, not gameplay eligibility. Water depth scans only the loaded column below the fish for up to 8 blocks, and no flow physics are modified.

## Water FX / accessibility

- **Ripple**: subtle near a wandering `MYTHIC`-profile fish.
- **Bubble trail**: approaching fish, or low-frequency idle fish.
- **Lure pulse**: owner-only once the admin's hook is accepted by the native probe.
- **Bite disturbance**: owner-only at the bite/finish cue.
- Fish-local effects are never sent to someone who lacks visibility of that fish; they wait until the fish entity was actually sent to the viewer.
- Honors player HUD/FX settings (`OFF` or `REDUCED_MOTION`) through `FishingPresentationService.particles`.
- No extra TextDisplay / scoreboard / fake items are created.

`water-fx.max-particles-per-tick` limits the **requested particle count** in this service. It is a **separate** budget from the ambient PacketEvents `max-packets-per-tick`; it does **not** represent measured network packets or bytes.

## Defaults and performance expectations

| Config key | Default |
|---|---:|
| `environment.max-samples-per-tick` | 4 |
| `environment.cache-entries` | 256 |
| `environment.cache-ttl-ticks` | 200 |
| `environment.depth-scan-blocks` | 8 |
| `water-fx.interval-ticks` | 10 |
| `water-fx.max-particles-per-tick` | 32 |
| `water-fx.max-particles-per-viewer` | 8 |
| `water-fx.candidates-per-viewer` | 24 |
| `water-fx.radius` | 16.0 blocks |

The FX emission order rotates viewers. Source reads are capped per tick, caches are bounded and cleared on reload/stop. Chunk water edits and unloads invalidate sampled chunks; no periodic global block scan is introduced by Phase 13.

## Diagnostics and emergency rollback

`/emf admin simulator status` includes a new `ecosystem-status` line:

- `enabled`, `cache`, `samples`, `sample-drops`
- `shy` (escape cues), `particles`, `fx-drops`

`/emf admin simulator off` disables Simulator preview. For less disruptive tuning, set `water-fx.enabled: false` and reload. If client FPS/ping suffers, reduce particle budgets or raise the interval; if CPU suffers, reduce samples/tick and candidates/viewer.

The following release gates remain **unverified by these source tests**:

- G3: native `FishHook` loot/XP suppression on Paper.
- G5: packet fish/cinematic appearance and cleanup on actual Minecraft clients.
- G7: catch/EMF reward/stats/database consistency on Paper.
- Full Gradle compilation remains unavailable if the Wrapper cannot download Gradle 9.5.1.

Never mark Phase 13 production-ready merely because headless tests pass.
