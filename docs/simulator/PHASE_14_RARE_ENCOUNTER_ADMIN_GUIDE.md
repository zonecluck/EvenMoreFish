# 🎣 Premium Phase 14 — Rare Encounter Admin Guide

**Staging only.** This module is visual-only and does not make rare fish more likely to bite.
It is not a replacement for `FishAttractionPolicy`, `CatchCommit` or Paper G3/G5/G7 validation.

## Requirements before trying it

1. Deploy a build of EvenMoreFish compiled with the required PacketEvents adapter to a **test Paper server**.
2. Finish G3 (native loot/XP), G5 (client fish packet visibility), G7 (catch/reward consistency).
   Until those pass, restrict testing to staff preview without reward.
3. Create a separate Simulator WORLD or CUBOID region. Do not test in a production fishing pond.
4. Keep `simulator.yml` legacy fallback and all feature flags OFF by default.

## Configuration

`plugins/EvenMoreFish/simulator/encounters.yml` (copied on first use)

- `enabled`: false by default. Enabling only works when master Simulator and ambient preview
  are intentionally enabled; it does not enable public fishing.
- `limits.max-active`, `max-per-zone`: upper bound on cosmetic encounter references, **not additional fish**.
- `limits.scan-interval-ticks`, `candidates-per-scan`: bounded rare-candidate sampling.
- `limits.life-ticks`, `zone-cooldown-ticks`, `viewer-cooldown-ticks`: anti-spam controls.
- `discovery.activation-chance`: applied ONCE per eligible existing fish, not per tick; does
  **not** change EMF rarity/fish item probabilities or grant any reward.
- `discovery.eligible-rarities`: genuine EMF rarity IDs. An unknown ID simply matches no fish.
- `discovery.fish-overrides`: exact `Rarity:FishId` to effect-type mapping. For example:

```yaml
  fish-overrides:
    'Legendary:Golden Koi': GOLDEN
    'Epic:Night Eel': GHOST
```

- `types.*.enabled`, `weight`: profile availability and relative cosmetic selection.
  An exact FishKey override may select an enabled type even when its random weight is zero.
- `types.*.night-only`: requires real `NIGHT` snapshot.
- `types.*.deep-only`: requires an environment snapshot of `DEEP` water.
- `types.*.min-school-size`: `RARE_SCHOOL` needs existing eligible school members; all FishKeys
  remain unchanged. Requires `simulator/schooling.yml` to be enabled.
- `visuals.*`: strict visual cue interval, distance and particle budget.

**Important:** `GHOST` and `SHADOW` require the Phase 13 `ecosystem.yml` environment sampler to
be enabled. If it is disabled or out of budget, these conditions fail closed (the director
will not invent a night/depth reading).

## Administrator commands

```text
/emf admin simulator status
/emf admin simulator encounter preview rare_presence
/emf admin simulator encounter preview golden
/emf admin simulator encounter preview shadow
/emf admin simulator encounter preview ghost
/emf admin simulator encounter preview rare_school
/emf admin simulator reload
/emf admin simulator off
```

Requires `emf.admin.simulator.preview` for visual preview; status uses the existing status
permission and reload uses the existing reload permission. Tab completion supplies supported kinds.

**Preview behavior:** the command takes an already rendered, existing, eligible, nearby fish.
No suitable fish or disabled encounter preview = no animation. It **never** spawns a guaranteed
Legendary, grants an item or updates a leaderboard.

## What the player sees

- **RARE_PRESENCE:** delicate white points following one fish in the water.
- **GOLDEN:** small gold-colored dust particles in an orbit around a real fish.
- **SHADOW:** dark drifting ink in deep water.
- **GHOST:** soft soul particles during the night.
- **RARE_SCHOOL:** subtle enchanted glimmers that rotate across real school members.

This is **ambient particle art**, not a custom fish texture or an additional cinematic scene.
When a catch is actually committed, the existing Phase 11 cinematic still handles the reward reveal.
Any premium rarity/FishKey cinematic override remains controlled by `simulator/cinematics.yml`.

## Anti-leak / fail-safe behavior

- A PRIVATE encounter cannot be shown to a stranger, even when they stand on the same block.
- A fish must already be sent to a viewer via `PacketVisibilityService.shownTo` before FX appear.
- Disabled particles in `/emf hud` also disable encounter particles.
- Missing fish, unload, quit/world-switch, reload or emergency-off cancels the encounter references.
- Invalid YAML rejects reload; previous valid snapshot remains active.
- A particle error disables cosmetic encounters rather than affecting `CatchCommit`.

## Rollback

1. Set `simulator/encounters.yml: enabled: false`, then `/emf admin simulator reload`.
2. If packet backend fails, use `/emf admin simulator off`.
3. Restore the previous Phase 13 source/JAR on a test server if required.
4. Do not set `paper-gates-verified: true` without actual client/Paper evidence.
