# 🎣 NativeHook Probe — Paper G3 Acceptance Matrix

## Setup (TEST SERVER ONLY)

- Backup player inventory + worlds + EMF config/database.
- Build Paper 1.21.4 or the intended 1.21.x target. Re-run on 1.21.11 before claim.
- First test baseline: `simulator.enabled: false`; verify normal EMF catches and vanilla treasure continue unchanged.
- Grant operator only: `emf.admin.simulator.probe`, admin status/trace permissions.
- Create one small isolated `CUBOID` around a known pond in `simulator.yml`, enable it.
- Set `simulator.enabled: true`, `simulator.native-hook-probe.enabled: true`,
  `simulator.dependency.on-unavailable: LEGACY_EMF`.
- Reload using `/emf admin simulator reload` and inspect `/emf admin simulator status`, `/emf admin simulator zone list`, `/emf admin simulator here`.
- Probe is deliberately rewardless. **Do not enable for ordinary players.**

## Runtime cases

| ID | Action | Expected |
|---|---|---|
| G3-01 | Cast as operator inside pond, wait until hook settles | `PROBE_TAKEOVER` appears in trace; no native bite naturally occurs |
| G3-02 | Reel after 2 seconds, before any simulator bite | `REEL_IN` -> `SESSION_CLOSE`; no items, no XP, one cleanup |
| G3-03 | Hold hook for 30+ seconds | No native `BITE`, `CAUGHT_FISH`, loot or XP; probe expires/cleans |
| G3-04 | Cast as ordinary player in same pond | Vanilla EMF catch/treasure works normally, no probe takeover |
| G3-05 | Cast outside pond | Vanilla EMF works; no probe takeover |
| G3-06 | Cast in overlap WORLD/CUBOID | Higher priority zone chosen, deterministic; deny zone overrides |
| G3-07 | Use OFF_HAND, then swap or replace the cast rod | Probe closes; no reward or stale session |
| G3-08 | Quit/teleport/change worlds/lose permissions during cast | Session/hook cleanup; no item or XP grant |
| G3-09 | Invalid YAML reload while probe is active | Old valid snapshot retained; no accidental session transition |
| G3-10 | Valid YAML reload while probe is active | Session canceled; hook removed, no leak |
| G3-11 | WorldGuard/RedProtect provider missing | Provider zone never activates; legacy fallback applies |
| G3-12 | WorldGuard denies fishing at caster or hook | No Simulator owner bypass; cancellation respected |
| G3-13 | mcMMO/AuraSkills modify fishing | No double native/EMF item, no illegal event uncancel |
| G3-14 | Lure III/luck rod/offhand/near boundary | Native bite postponed, stable event order |
| G3-15 | Force a native `CAUGHT_FISH` during owned session | Event canceled, EXP=0, item not delivered; log & clean |
| G3-16 | Spam cast/reel and switch worlds | Pending ≤ 64; active probes ≤ configured max; no lag spikes |
| G3-17 | Plugin disable/restart mid-cast | Scheduler stopped, no leftover hook displays/records |
| G3-18 | Compare baseline outside configured zones | Catch rates, bait, custom rod, competition behavior unchanged |
| G3-19 | Set on-unavailable DENY in test pond | Cast lands, removes hook with clear localized warning; no item |
| G3-20 | Invalid region bounds/unknown enabled world | Loader rejects reload and keeps previous valid snapshot |

## Collection / proof

- Save Paper build number, JVM 21, relevant plugin versions, exact config snapshot.
- Record `/emf admin simulator trace` output from at least 20 casts per case.
- Record inventory + EXP **before and after** every scenario; detect vanilla Item entities at catch.
- Run timings profiler (spark) during 10+ simultaneous casts; note MSPT, task cost and warnings.
- Do not approve G3 until repeatable on 1.21.4 and the final target version with no double reward.

## Immediate rollback

`/emf admin simulator off` then edit `simulator.yml` => `simulator.enabled: false` => `/emf admin simulator reload`.

The feature is default OFF and core EMF catch pipeline stays intact.
