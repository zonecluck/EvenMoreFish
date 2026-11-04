# Phase 8 — Paper Runtime Test Matrix (staging only)

**Status:** NOT EXECUTED on Paper. These boxes are mandatory sign-off criteria, not passes inferred from mocks. Target Paper 1.21.4+, Java 21, compatible external PacketEvents runtime. Folia unsupported.

## Build + gating (BLOCKERS)
- [ ] `./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon` compiles on a dependency-resolving machine.
- [ ] Plugin enables with PacketEvents missing; Simulator visuals fail closed and legacy EMF remains usable.
- [ ] Verify Gate G3: no native bite/loot/XP during active owner probe; real CAUGHT_FISH or native BITE cannot escape.
- [ ] Verify Gate G5: COD/SALMON/TROPICAL_FISH/PUFFERFISH virtual fish spawn/move/despawn client-side, no ghosts.
- [ ] Verify Gate G7: exact FishKey/reward/event/stats semantics; double reels, canceled addon event, bait, rod swapping, full inventory and DB failure.
- [ ] All Simulator and effects toggles start disabled after fresh install; no production reward bridge enabled.

## HUD + sounds/particles
- [ ] Interested: configured small bubble cue only for authorized angler.
- [ ] Bite: immediate localized actionbar plus countdown; verify <=5 updates/s with default interval-ticks=5.
- [ ] HUD progress bar decreases and reaches zero; does not persist after early reel, timeout, quit or new cast.
- [ ] `messages_vi.yml`, `messages_vn.yml`, `messages_en.yml` display correct glyphs and MiniMessage.
- [ ] Untranslated locale uses packaged English fallback, never raw keys or stacktrace.
- [ ] Rarity presets give noticeably different cues; fish override matches exact rarityId:fishId.
- [ ] Sound NONE/particle NONE produce no respective effect.
- [ ] Sound/particle config invalid: reload rejects entirely and preserves last-good config/session, no crash.
- [ ] Harmful/invalid particle data requiring complex custom payload is rejected during config load.

## Packet cinematic & identity
- [ ] A SUCCESS_PREVIEW_ONLY reel starts a viewer-private packet fish arc from real hook position to angler, **without reward**.
- [ ] A SUCCESS_COMMITTED admin test runs cosmetic only **after** reward delivery succeeds, not before EMFFishCaughtEvent.
- [ ] Canceling EMFFishCaughtEvent yields no success cinematic, no bait charge, no stats/XP/reward.
- [ ] Two players near one shared fish: no ghost animations in the other player's client, max one active reservation.
- [ ] Private fish animation remains visible only for owning player.
- [ ] 4 concurrent cinematics hit cap, fifth skipped; 12-packet FX budget covers spawn+metadata and movement (destroy/cleanup is intentionally exempt).
- [ ] World change/dimension change/quit during cinematic despawns fake fish immediately or by next tick.
- [ ] `/emf admin simulator effects off` cancels HUD and ongoing packet cinematic for command issuer; on resumes future cues.
- [ ] Starting a new cast cleans old cinematic and prevents overlapping virtual entity IDs.
- [ ] `/emf admin simulator reload`, emergency off, plugin disable all attempt packet cleanup; no duplicate reward.
- [ ] If PacketEvents is disabled or transport throws mid-cinematic: no repeated errors or reward actions; document any client ghost until relog.

## Safety and performance
- [ ] Run at least 30 clients fishing with 4 concurrent FX while 100 idle players are nearby, record spark profiler and network packets/MSPT.
- [ ] Compare effects off/on at identical load (record hardware, entity/chunk count); no invented MSPT thresholds.
- [ ] Repeated rapid reel and GUI/commands cannot create extra fish items or spend bait twice.
- [ ] Main-thread interaction only: FX renderer never calls a Bukkit world/player API from async.
- [ ] Legacy EMF in world outside Simulator zones passes catches, treasure, bait, shop, journal and competition regression.
- [ ] External WorldGuard/RedProtect canceled hooks never trigger Simulator or FX.

**Release blockers:** G3, G5 and G7 runtime evidence missing; Gradle full compile missing. Even passing this matrix does not establish Folia support.
