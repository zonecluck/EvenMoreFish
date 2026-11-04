# 🎣 EvenMoreFish Simulator V2 — Phase 9 Admin Operations & Rollback

> Status 2026-10-11: **STAGING SOURCE ONLY**. Neither a verified build nor a production fishing engine. This runbook is a procedure, **not** proof that any runtime gate has passed.

## 1. What has actually changed

- `PacketVisibilityService`: global destroy-before-spawn reconciliation; round-robin stale-entity cleanup; nearest-N selection with bounded memory; per-tick budget and failure/backlog metrics.
- `SimulatorModule`: a fatal exception in **any** preview ticker stage (region/native-hook/water/packet/AI) trips emergency mode, stops further ownership, attempts to release native hooks and visuals. AI start failure after hook takeover now explicitly ends the session.
- `AmbientPreviewService`: persisted packet diagnostic counters across stop/reload, preview tick sampling, water-job-drop counter.
- `/emf admin simulator doctor`: reports dependency/config/runtime conditions, **never** asserts G3/G5/G7 verified. `/emf admin simulator status` includes the performance line.
- Added isolated `qa/run-simulator-phase9-smoke.sh` + JUnit test for packet reconciliation.

## 2. Minimum server prerequisites (staging only)

1. Java 21, a clean Paper 1.21.4+ test instance matching the actual variant; PacketEvents **2.14.0** is a build dependency and an external optional runtime dependency.
2. Build on a machine capable of resolving Gradle and Maven dependencies:
   ```bash
   ./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon
   ```
   Confirm **exit code 0 and resulting JAR** before placing anything in `plugins/`. Do not install a ZIP of source files into `plugins/`.
3. Make a **full backup** of EMF plugin configs (`config.yml`, `messages.yml`, locale overrides, rarity, bait, rod, competition) and its database before any test involving reward/storage.
4. Start with the following controls **disabled**, even when the plugin loads correctly:
   ```yaml
   simulator:
     enabled: false
     ambient-preview:
       enabled: false
     ai-preview:
       enabled: false
     native-hook-probe:
       enabled: false
     reward-bridge:
       enabled: false
       paper-gates-verified: false
   ```
   The first four keys belong to `simulator.yml`; visual/cinematic flags belong to `simulator/effects.yml`. `reward-bridge.paper-gates-verified` is an **admin acknowledgment flag**, not a runtime assertion.

### Worlds loaded after EMF (2026-11-01)

`simulator.yml` validates an enabled zone's `world` as a nonblank string. A valid world configured by a multi-world plugin may load after EMF enables; this no longer rejects the entire last-good Simulator snapshot. Blank/non-string world names, malformed cuboid bounds and missing region-provider IDs still fail validation.

The zone stays dormant when a real player/hook location has no world or belongs to another world. The same immutable snapshot matches once the configured world supplies an actual location. Holograms already retry on world/chunk load and require the world and target chunk to be loaded; this fix does not create worlds or load chunks to satisfy configuration. A misspelled nonblank world remains dormant and should be diagnosed with `here`/`zone info` and the world manager.

No Simulator, probe, ambient, AI or reward flag is enabled by this fix. Live flags that were false stay false; it does not certify Paper/client gates or public Simulator mode.

## 3. Progressive verification on a nonproduction server

1. Baseline: all Simulator flags OFF. Test EMF catches, rarity, custom rods, bait, shop, competition and protections with PacketEvents absent/present.
2. G3 native probe: create one CUBOID/WORLD region in `simulator.yml`; grant `emf.admin.simulator.probe`, use one real admin fishing hook; capture `PlayerFishEvent` traces while repeating casts. Test canceled events, offhand, swapped rod, cross-world, idle timeout and plugin disable. **Any duplicate vanilla fish or XP = FAIL**.
3. G5 packet preview: enable only `ambient-preview.enabled` (plus root `simulator.enabled`) in test; grant `emf.admin.simulator.preview`. Test 2 clients for SHARED and PRIVATE fish, reload/quit/world-change cleanup, missing PacketEvents. Enable AI preview **only after** stable G3/G5 evidence.
4. G7 test reward: only on staging after G3+G5 proof, manually set reward flags. Test fish identity/size, custom bait+rod, canceled `EMFFishCaughtEvent`, full inventory, economy hooks, competition, database timeout, reload and server crash boundaries. No real player rollout in this source; only admin probe bridge exists.
5. Phase 8 effects: after packet test, optionally enable `effects.enabled`; test HUD, FX budget and cinematic, then disable and verify no ghost fish.
6. Phase 9 profile: use `spark` or equivalent Paper profiler plus packet and network instrumentation at 1, 10, 30, 50, 100 **active clients**. Currently admin viewer cap is 32 and AI max-active is 16; these higher target sizes **cannot yet be tested as normal Simulator gameplay without further implementation**. Do not present synthetic tests as Paper MSPT.

## 4. Operational commands

```text
/emf admin simulator status       # includes average/peak preview tick time, packet failures/backlog
/emf admin simulator doctor       # config/backend snapshot + explicit unverified-gates warning
/emf admin simulator trace        # last 15 of 128 bounded native probe events
/emf admin simulator here         # resolve current zone
/emf admin simulator zone list
/emf admin simulator zone info <id>
/emf admin simulator effects on|off
/emf admin simulator reload       # validate and keep last-good snapshot on invalid YAML
/emf admin simulator off          # emergency shutoff, release sessions and visuals
/emf admin simulator on           # resume only currently configured ADMIN preview; no player rollout
```

The diagnostic values are **telemetry**, not pass/fail proof:

| Counter | Interpretation | Operator action |
| --- | --- | --- |
| `pending destroys` | Stale client entity IDs waiting for destroy | Investigate if it never returns to zero |
| `packet failures` / `destroy failures` | Failed outbound visual packets; historical across reload | Check PacketEvents version, backend and connection |
| `packet peak` | Most packet-budget units used in a single preview tick | Compare with configured `packets-per-tick` |
| `avg/max preview ms` | Wall-clock cost of preview callback in this JVM | Compare spark trace before/after enabling visuals |
| `over2ms` | Count of sampled preview ticks above an **advisory** 2 ms mark | Inspect spikes; this is not a validated production SLO |
| `ticker failures` / `last reason` | Unexpected error that activated emergency mode | Keep OFF and inspect console; do not blindly resume |
| `water jobs dropped` | Index work rejected by bounded queue | Reduce scanning radius or viewer count; verify water index freshness |

No per-player raw IP, bait secrets or database credentials are logged by the new instrumentation.

## 5. Failure drills

| Injection | Required result |
| --- | --- |
| PacketEvents missing on startup | Normal EMF catches unaffected; preview unavailable |
| PacketEvents removed/tripped while preview active | Preview stops; no reward from visuals; client ghosts may require relog |
| Malformed zone/world-name/config reload | Last-good snapshot kept; existing sessions cleaned only on successful reload |
| Native `CAUGHT_FISH` during owned probe | No duplicate vanilla loot or EXP (verify with real server) |
| Internal packet transport `destroy` throws | Other viewer cleanup still attempted; failure/backlog observable |
| Fish catalog reload / world change / quit | All stale sessions and private fish cleaned |
| Inventory full / item drop canceled | No replay or dupe; **potential partial-delivery/stat mismatch remains a release blocker** |
| DB timeout / rollback | No main-thread blocking or false catch stats (must test with actual provider) |

## 6. Rollback playbook

**Immediate (while server runs):** run `/emf admin simulator off`. It is a best-effort cleanup. Then disable Simulator flags and leave `paper-gates-verified: false` on disk before reload/restart.

**Planned rollback:** stop server; back up current logs/data; replace the test JAR with the previous known-good EMF JAR; restore matching backed-up configuration/DB only if needed and after evaluating new catches. Do not overwrite live player data with an older DB indiscriminately. Start server, verify normal legacy catches/shop/history/competition, compare world entity counts, request testers relog if any packet-only ghost remains.

**Security:** grant `emf.admin.simulator.manage`, `.probe` and `.preview` only to trusted staff. Never accept a packet-only entity ID or client timestamp as reward authority.

## 7. Remaining hard blockers

- **Build:** Gradle 9.5.1 download has failed with `UnknownHostException` in the supplied environment; no shaded JAR was verified.
- **Runtime:** Paper native loot suppression (G3), client packet spawn/movement/despawn (G5), item/DB/competition commit (G7) were **not** executed.
- **Gameplay scope:** standard users are still routed through legacy EMF; this source contains **admin-only Simulator** rather than a production-ready public Simulator.
- **Compatibility:** no native treasure/bait-loot agent equivalence; fallback to EMF legacy remains deliberate. No Folia claim.
- **Durability:** Bukkit inventory/drop hooks and external reward commands do not guarantee cross-crash exactly-once semantics. Explicit ledger/outbox architecture is needed before claiming such durability.

**Release rule:** do not enable normal-user Simulator or claim G9 before all P0/P1 blockers above have been resolved, tested, and signed off with server logs/profiles.
