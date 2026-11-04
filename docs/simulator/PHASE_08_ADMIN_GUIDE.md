# 🎣 EvenMoreFish Simulator V2 — Phase 8 Admin Guide

> **Release state:** Staging/admin-only. **Do not** deploy as a production Simulator, even if the build succeeds. Phase 3 native-hook suppression, Phase 5 packet visuals and Phase 7 reward G7 have **not** been validated on a real Paper instance here.

## 1. What Phase 8 actually adds

- `simulator/effects.yml` for four sound/particle/cinematic presets (`common`, `rare`, `epic`, `legendary`).
- Owner-only `BITE_READY` actionbar countdown (throttled, default every 5 ticks). No HUD entity.
- A short packet-only fish arc after a **successful reel**. A separate virtual fish entity ID is created and destroyed just for the angler; this never alters server fish population or gives rewards.
- `/emf admin simulator effects on|off` toggles your own preview FX for the current login session (not saved to SQL/YAML).
- `/emf admin simulator status` gains visual queue/HUD/packet counters.
- Cleanup on new cast, quit, zone exit (session validation), dimension change (next FX tick), reload, emergency-off and plugin disable.
- English fallback strings for other packaged locales; Vietnamese translations in `messages_vi.yml` and `messages_vn.yml`.

## 2. Staging installation order

1. Build Gradle project on a machine with network/dependency cache. Install a supported external PacketEvents runtime jar before attempting visual preview.
2. Use **Paper**, not Folia. Keep the existing EMF plugin outside Simulator areas working as before.
3. First test the legacy catch regression, G3 native hook suppression (no vanilla loot or XP), and G5 PacketEvents rendering. Do not assume unit/smoke tests prove these gates.
4. Configure one disabled-by-default cuboid or WorldGuard zone in `plugins/EvenMoreFish/simulator.yml`. Only turn on `simulator.enabled`, `ambient-preview.enabled`, `native-hook-probe.enabled`, `ai-preview.enabled` in isolated staging.
5. Assign the test admin both `emf.admin.simulator.preview` and `emf.admin.simulator.probe`.
6. Keep `reward-bridge.enabled: false` and `paper-gates-verified: false` until runtime gates pass. **Phase 8 animations also run for successful no-reward preview reels.**
7. In `plugins/EvenMoreFish/simulator/effects.yml`, set `effects.enabled: true`, leave `cinematic.enabled: false` for initial HUD/sound testing, then enable the cinematic as a separate test.
8. Test `/emf admin simulator status`, `effects off`, `effects on`, `trace` and `reload` (with permission).

## 3. Zone examples

### CUBOID

```yaml
zones:
  simulator_pond:
    enabled: true
    deny-simulator: false
    type: CUBOID
    world: world
    min: {x: -35, y: 50, z: 80}
    max: {x: 35, y: 100, z: 140}
    priority: 60
```

### WORLDGUARD_REGION

```yaml
zones:
  fishing_harbor:
    enabled: true
    deny-simulator: false
    type: WORLDGUARD_REGION
    world: world
    region-id: fishing_harbor
    priority: 80
```

The actual WorldGuard region must exist. If no provider/region exists, Simulator must fail closed and preserve legacy EMF policy. Overlapping deny zones take precedence. Zone selection uses the **settled hook** position, not only where the player stands.

## 4. Effect presets and custom-fish overrides

`simulator/effects.yml` has independent `effects.enabled` and `cinematic.enabled`; both default `false`. `hud.enabled` only takes effect when the outer `effects.enabled` is true.

```yaml
rarity-presets:
  Common: common
  Rare: rare
  Legendary: legendary
fish-overrides:
  'Legendary:Golden Carp': legendary
```

The `fish-overrides` key must match EMF's `rarityId:fishId`, not an item display name guessed from lore. If an override does not match, the rarity preset applies; otherwise `common`.

**Allowed (safe/simple) particle IDs:** `SPLASH`, `BUBBLE_POP`, `FISHING`, `HAPPY_VILLAGER`, `CLOUD`, `CRIT`, `ENCHANT` or `NONE`. The server API's older names are accepted where supported. Complex particles requiring additional data are intentionally blocked. Invalid sound/particle/number/glyph/config-version aborts reload without replacing the prior FX snapshot. Sounds use Bukkit enum IDs and Adventure.

The visual fish model is resolved by the existing `simulator/visuals.yml`. Actual resource-pack `ITEM_DISPLAY` metadata is **not implemented yet**. Misconfigured custom-model renderers continue to fall back to vanilla fish visuals.

## 5. Budget and performance

| Budget | Default | Bound |
|---|---:|---:|
| Max simultaneous catch cinematics | 4 | 1–8 |
| Cinematic packet budget per tick | 12 | 2–32 |
| Cinematic duration | 14/20/28/36 ticks by preset | 4–40 |
| Maximum hook-to-player range | 32 blocks | 4–48 |
| Actionbar refresh interval | 5 ticks | 4–20 |
| HUD segments | 12 | 4–24 |
| Particle count for a cue | preset-specific | 0–24 |

The packet budget above is an **additional FX soft budget for spawn + metadata + movement**, separate from the ambient fish visibility budget in `simulator.yml`. A new spawn reserves 2 packet slots within its real server tick. Cinematics send one virtual spawn/metadata pair, at most one teleport/tick and a destroy to the angler only. **Destroy/cleanup packets bypass the soft budget** so exiting players never wait for budget to become available; counters may therefore briefly exceed the configured limit on cleanup ticks. The animation does not lock player camera, block input, or spawn actual entities.

For a crowded staging server, start with `cinematic.enabled: false` and tune `ambient-preview.packets-per-tick` independently. `effects off` disables your HUD, sounds and cinematic preview for your current session, and disconnect clears the preference.

## 6. Diagnostics and rollback

- `status` shows ambient packet budget, active sessions and new FX counters.
- `trace` reports server-state events; it is not a client rendering log.
- `off` should immediately cancel active Simulator sessions and attempt virtual-entity cleanup; outside configured zones vanilla EMF is unchanged.
- If an effects reload is invalid, fix `simulator/effects.yml` and rerun `reload`. Last-good active config stays in memory.
- To rollback visuals only: set `effects.enabled: false` and reload.
- To rollback Simulator: run `/emf admin simulator off`, then set `simulator.enabled: false` in `simulator.yml`, reload.
- Report full logs and Java/Paper/PacketEvents version if packet metadata fails. A transport failure after spawn may leave a client-side ghost until logout/world reload; it **must never trigger a second reward**.

## 7. Known limitations

- **No full compile or Paper runtime pass in the current environment** because Gradle Wrapper cannot download distribution. The independent Java smoke tests use mocked Bukkit interfaces.
- No promised Folia compatibility.
- Sound and particle FX are sent to the angler only, intentionally avoiding leaks from private fish; nearby-player audience presets are not implemented in Phase 8.
- The opt-out preference is per login, not persisted across reconnects.
- An FX failure is caught in Simulator callbacks and must not change the authoritative `CatchCommit` result; production verification still required.
- Simulator remains admin preview only. No brand-new production gameplay progression, resource pack content, or automatic enablement is added.
