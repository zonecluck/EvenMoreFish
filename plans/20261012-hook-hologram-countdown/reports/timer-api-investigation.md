# Native fishing countdown investigation

Date: 2026-10-12. Read-only investigation; no fishing/reward/probe behavior changed.

## Findings

`FishHook#getWaitTime()` is a **live countdown**, not the configured wait range. CraftFishHook returns NMS `timeUntilLured` directly. It measures the phase before a fish appears. It is not the complete remaining time to BITE: the next approach phase is randomized only when this countdown expires.

| Phase | Native field | Public getter | Meaning |
| --- | --- | --- | --- |
| Waiting for fish appearance | `timeUntilLured` | `int getWaitTime()` | Current native wait countdown, already incorporating Lure and existing pool changes |
| Fish approaching hook | `timeUntilHooked` | `int getTimeUntilBite()` on cached 1.21.1/1.21.3/1.21.4/1.21.8/1.21.11 APIs | Current native swim/approach countdown |
| Fish biting; player may reel in | private `int nibble` | None in cached APIs through 1.21.11 | Actual remaining successful reel-in window |

Cached 1.20.1 and 1.20.4 APIs have `getWaitTime()` but **do not** have `getTimeUntilBite()`. `getMinWaitTime()`, `getMaxWaitTime()`, `getMinLureTime()`, and `getMaxLureTime()` are configuration bounds, never truthful substitutes for live timers. HookState `BOBBING` covers waiting, approaching and biting; it cannot distinguish these phases by itself.

## Native timing semantics and event ordering

The cached patched 1.20.4, 1.21.4 and 1.21.11 FishingHook source all contain the same phase ordering:

1. If `nibble > 0`, decrement it by exactly one active native fishing tick; when it expires, clear wait/approach and fire FAILED_ATTEMPT.
2. Else if `timeUntilHooked > 0`, decrement approach by weather-sensitive `i`, then fire BITE on expiry.
3. Else if `timeUntilLured > 0`, decrement wait by `i`; initialize a random approach count on expiry.
4. Else initialize native wait, incorporating Lure.

`i` starts at 1, can be increased for rain and decreased for blocked sky, therefore wait/approach countdowns can decrease by 0, 1 or 2 per active native fishing tick. Dividing by 20 gives nominal native-tick seconds, **not a guaranteed wall-clock ETA**. Server lag and leaving water can delay real elapsed time further. It is still a live native count, unlike a presentation-owned countdown.

Crucially, BITE is fired **before** `nibble = Mth.nextInt(random, 20, 40)`. Read the bite window on the next scheduled hologram update, not inside `showBite()` invoked by the event. Do not initialize an assumed 20/30/40 tick window in that event. If a later listener cancels BITE, native nibble remains uninitialized.

## Recommended bounded, read-only access

- Read public `getWaitTime()` directly.
- Resolve the optional public `getTimeUntilBite()` by reflection once per hook implementation class, because core compilation targets 1.20.1.
- Resolve CraftFishHook `getHandle()` and exact declared integer fields `nibble` and, when the public approach getter is absent, `timeUntilHooked`. Cache the accessors; use read-only `Field#getInt`, never a setter. Validate the returned handle class/field types rather than guessing from field order or scanning every integer each tick.
- Fail closed for unknown or inaccessible mappings: retain phase text/bar styling without a fabricated numeric countdown. No repeated per-tick reflection discovery or repeated failure log.
- BITE text remains animated in TextDisplay only. Existing BITE `showTitle` and associated `clearTitle` calls are independent screen overlays and should be removed for hologram-only behavior. `removeDisplay()` currently clears a player's title even without owning one, so retaining that cleanup can interfere with unrelated plugin titles.

For legacy support, the cached **1.20.4** dev-bundle mapping is exact:

```text
net.minecraft.world.entity.projectile.FishingHook -> net.minecraft.world.entity.projectile.EntityFishingHook
int nibble -> j
int timeUntilHooked -> l
int timeUntilLured -> k
```

These aliases were verified against `data/mojang+yarn-spigot-reobf.tiny` in the cached 1.20.4 dev-bundle. There is no cached 1.20.1 dev-bundle/server here; the 1.20.1 API was verified with `javap`, but **do not claim its obfuscated field aliases were empirically verified**. For an implementation limited to proven mappings, use literal Mojang fields, verified legacy 1.20.4 aliases, and an explicit unknown-data fallback. Additional legacy aliases require actual target-artifact verification.

## Presentation constraints

The two-line hologram may show phase heading plus `Đang chờ cá [bar] 1.3s` for the currently active native wait/approach phase. During BITE use the actual nibble count with `Hãy giật cần` on the first line. If the requirement is a single exact countdown all the way from casting to BITE, native Minecraft does not expose an already chosen approach count during waiting: it has not yet been randomized. Do not add max/min lure duration to wait, rerandomize timers, call `resetFishingState()`, or use a local elapsed timer to manufacture that total.

A bar may normalize against the first observed positive count of each native phase; this is presentation state only. Read live counts afresh every update so FishingPoolService reductions/restorations remain visible. Do not change that service's timers, Lure restoration, rewards, cancellation or probe logic.

## Evidence

- Existing source: `even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/FishingHookHolograms.java`; `fishing/pool/FishingPoolService.java` reads and reduces live `getWaitTime()` after native initialization.
- Cached Paper API 1.20.1 `71680be2f2e80f25e20ae4b49ebd84539d306a44/paper-api-1.20.1-R0.1-SNAPSHOT.jar`: `javap org.bukkit.entity.FishHook` confirms public wait getter and missing approach getter.
- Cached Paper API 1.21.11 `a3a811fd1e99bce749c4e6db9991e45294438d00/paper-api-1.21.11-R0.1-SNAPSHOT-sources.jar`, entry `org/bukkit/entity/FishHook.java`: signatures and approach Javadoc.
- Cached paperweight-userdev `applyDevBundlePatches_69326811c945b51fde644946656dbe1691f96a875af4c51a6b9adb14acb39d91/output.jar`, version.json=1.20.4: CraftFishHook lines 201-202; FishingHook lines 65-67, 314-419.
- Cached paperweight-userdev `applyDevBundlePatches_8296811d0593b352ab16b08f005df682241489b414761d106df4bd30e1e65809/output.jar`, version.json=1.21.4: CraftFishHook lines 201-212; FishingHook lines 58-60, 304-433.
- Cached paperweight-userdev `applyDevBundlePatches_c9a53f01acfb65c4a1dc2a7800c5c82b06fd8bca489383ff45ca9abd7808383a/output.jar`, version.json=1.21.11: CraftFishHook lines 203-214; FishingHook lines 61-63, 316-442.
- Cached 1.20.4 dev-bundle `9e18f6d72b556d1466cf496eb45c79eca2f528f1/dev-bundle-1.20.4-R0.1-SNAPSHOT.zip`: exact legacy field mapping above.
- Official source corroboration: [Paper 1.21.11 CraftFishHook](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftFishHook.java), [Paper 1.21.11 FishingHook patch](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/patches/sources/net/minecraft/world/entity/projectile/FishingHook.java.patch).

Status: DONE_WITH_CONCERNS

Concerns: Exact legacy 1.20.1 obfuscated nibble/approach field aliases were not available locally; numeric unknown-phase fallback is required unless that exact target artifact is separately verified. No actual server/client countdown rendering was exercised by this investigation.
