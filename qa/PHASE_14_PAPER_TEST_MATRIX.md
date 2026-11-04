# Premium Phase 14 — Paper & Client Acceptance Matrix

**All cases UNVERIFIED until executed on a real Paper staging server and recorded with logs/footage.**
Headless Java smoke test results do not count as Paper/MSPT evidence.

## Boot/config

- [ ] Full Gradle compilation + JUnit on Java 21; clean enable/disable with PacketEvents.
- [ ] Default `simulator/encounters.yml` copied; `enabled: false` never sends FX.
- [ ] Invalid numeric, NaN, wrong boolean, empty rarity list, bad FishKey override rejects reload atomically.
- [ ] External zones absent (WorldGuard/RedProtect missing) fail closed.
- [ ] Config reloaded 100 times; no stale runtime/FX/packet IDs and no memory growth.

## Visibility & identity

- [ ] GOLDEN attaches only to eligible existing rare FishKey, not a new/phantom fish.
- [ ] Common fish can never be labeled Legendary by a visual encounter.
- [ ] Override `Legendary:Golden Koi` matches only that exact existing FishKey.
- [ ] Two people in same shared zone see the same fish/encounter, subject to viewer cooldown.
- [ ] PRIVATE fish and encounter FX are invisible to the other viewer.
- [ ] Cross-world, cross-zone, region boundary never leaks encounter particle.
- [ ] Fish outside packet view distance gets no FX, even when it exists server-side.
- [ ] `RARE_SCHOOL` uses 2–4 *existing* same-scope eligible fish and does not add fish population.
- [ ] Fish moves, is reserved, despawns, chunk unloads, player disconnects: visuals track/cleanup.

## Conditions/gameplay

- [ ] GHOST requires night; SHADOW requires actual deep-water snapshot.
- [ ] Disabled environment sampler prevents GHOST/SHADOW; no fabricated readings.
- [ ] One eligible fish cannot repeatedly roll cosmetic encounter by AFK/tick spam.
- [ ] Cooldowns and active caps persist for runtime instance; do not explode under 100-viewer load.
- [ ] Fish AI reservation never overwritten by encounter; reel early/late unaffected.
- [ ] Preview command requires permission; console/player-only behavior and invalid input safe.
- [ ] Preview command with no real eligible fish reports unavailable and grants nothing.
- [ ] Successful *committed* catch updates encounter completed metric; preview success does not.
- [ ] EMFFishCaughtEvent cancel yields no item, bait consumption, XP or competition reward.
- [ ] Existing EMF vanilla fishing outside Simulator zone unchanged.

## Performance and rendering

- [ ] Client visual recording of each type at day/night/deep/near-shallow boundary.
- [ ] GOLDEN color and RARE_SCHOOL movement look smooth; no UI overlay collision.
- [ ] HUD OFF/REDUCED_MOTION suppresses ambient FX as configured.
- [ ] Particle cap never exceeded under 1/10/30/50/100 viewers; measure real MSPT.
- [ ] PacketEvents backend interrupted then restored: cleanup resumes, no ghost fish.
- [ ] Main-thread timeout/lag profiler: no forced chunk load or SQL from encounter code.
- [ ] Reload/quit/world change across 100 viewers clears state and uses no raw async Bukkit API.

## Required release gates (still open)

- [ ] G3 — Real native `FishHook` loot/EXP suppress.
- [ ] G5 — Actual PacketEvents spawn/metadata/movement/destroy on client.
- [ ] G7 — CatchCommit inventory/bait/event/stats/competition/DB integrity.
- [ ] Full compile + JUnit + runtime test + rollback rehearsal.
