# 🎣 Premium Fish Simulator — Phase 10B/11 staging admin guide

> **Staging/source only.** Do not deploy as production until full Gradle/Paper validation and G3/G5/G7 are complete.

## Feature flags and config

`plugins/EvenMoreFish/simulator.yml` must have a configured active zone and the following for staff fish packet preview (other production safety requirements still apply):

```yaml
simulator:
  enabled: true
  ambient-preview:
    enabled: true
  ai-preview:
    enabled: false  # Keep false for pure cinematic preview
  native-hook-probe:
    enabled: false  # Keep false for pure cinematic preview
  reward-bridge:
    enabled: false
    paper-gates-verified: false
```

`plugins/EvenMoreFish/simulator/effects.yml`:

```yaml
effects:
  enabled: true
cinematic:
  enabled: true
  max-concurrent: 4
  packet-budget-per-tick: 12
```

`plugins/EvenMoreFish/simulator/cinematics.yml`:

```yaml
config-version: 1
enabled: true
# Customize presets, sound/particle, stage duration and FishKey override here.
```

`plugins/EvenMoreFish/simulator/presentation.yml` controls Simulator HUD owner; `simulator/hud-gui.yml` enables `/emf hud` menu separately. If presentation is disabled, `CINEMATIC` preference still influences FX, while actionbar messages remain disabled.

### Admin preview

1. Install matching Paper/PacketEvents on **staging** and verify server logs and packet backend.
2. Give staff `emf.admin.simulator.preview`; enable Simulator ambient preview test zone and cinematic flags above.
3. Run `/emf hud cinematic` or use GUI if enabled.
4. Look at water within 12 blocks and execute `/emf admin simulator cinematic preview legendary` (choose `common`, `rare`, `epic`, `legendary`).
5. Run `/emf admin simulator cinematic stop` to skip immediately, including while preparing the scene.
6. Check `/emf admin simulator status` and `/emf admin simulator doctor` to inspect packet errors, orphan IDs, stage/trail counts, but **doctor is not proof of G3/G5/G7**.

The preview has a synthetic **visual-only** fish key, never generates fish items, catches, XP, bait consumption or leaderboard entries.

### Visual quality

| Profile | Visual policy |
|---|---|
| `OFF` | No Simulator HUD/FX |
| `MINIMAL` | Minimal bite cues; no cinematic |
| `BALANCED` | Short legacy packet-fish arc, restrained cues |
| `CINEMATIC` | Full multi-stage storyboard after catch |
| `REDUCED_MOTION` | Reduced motion; no multi-stage cinematic |

| Preset | Default duration | Character |
|---|---:|---|
| Common | 16 ticks | Quick water breach → delivery |
| Rare | 30 ticks | Short anticipation → leap → reveal |
| Epic | 48 ticks | Water swirl → leaping fish → highlight |
| Legendary | 70 ticks | Build-up → vortex → leap → hero pose → reveal/delivery |

Stage cues fire once on transition, trails are rate limited, no camera teleport, no time freeze, no real fish mobs. Rarity IDs map to presets in `simulator/cinematics.yml`; FishKey overrides use `rarityId:fishId`.

### GUI controls

- `/emf hud` opens the profile GUI only if `simulator/hud-gui.yml` `enabled: true`; otherwise shows profile usage text.
- `/emf hud <off|minimal|balanced|cinematic|reduced-motion>` works independently of GUI enable flag.
- GUI uses `InventoryHolder` and UUID sessions; it cancels item movement, shift/hotbar/double click and drag. Reload closes open GUI sessions.
- GUI config validates size/slots/materials/locale text and registry sound (`click-sound: minecraft:ui.button.click`).
- Current GUI selects *presets only*, not each individual layer; independent toggle GUI remains planned.

### Emergency rollback

1. `/emf admin simulator off` (stops Simulator owner/ticker; no new sessions).
2. Set `simulator/cinematics.yml enabled: false` and `simulator/hud-gui.yml enabled: false`.
3. Set `simulator/effects.yml` `cinematic.enabled: false`, `effects.enabled: false`.
4. Keep `simulator.yml` reward bridge and probe OFF; `/emf admin simulator reload` only after YAML validation.
5. If packet backend failed, disconnect affected player to clear client-local entity IDs; keep server in staging until cleanup verified.
6. Restore a backup of config/source if needed. Do not mark paper-gates-verified unless the Paper test matrix is fully completed.
