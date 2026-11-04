# Phase 9 — Paper / PacketEvents Production Hardening Test Matrix

**Status: NOT EXECUTED on a real Paper test server.** All boxes start unchecked. Offline JVM synthetic tests are documented separately and do not satisfy G3, G5, G7 or G9.

## A. Build, dependencies, shutdown

- [ ] BUILD-01: Gradle wrapper 9.5.1 resolves; `:even-more-fish-plugin:test` and `:even-more-fish-plugin-1-21:shadowJar` exit 0.
- [ ] BUILD-02: Correct JAR enables Paper 1.21.4 and 1.21.11 on Java 21; report actual build hash, versions and plugin startup logs.
- [ ] BUILD-03: PacketEvents absent, disabled on startup, and removed after start: no `NoClassDefFoundError` or corrupted listener state; legacy EMF catch continues.
- [ ] BUILD-04: Start/disable/reload x20; no ghost fish and no retained scheduler tasks; no synchronous JDBC on join or catch.
- [ ] BUILD-05: Other plugins WorldGuard, RedProtect, Vault, PlaceholderAPI, MMOItems, mcMMO, AuraSkills configured individually and in realistic combinations.
- [ ] BUILD-06: No Folia support flag or unsupported scheduler claim.

## B. G3 native hook ownership and anti-dupe

- [ ] G3-01: Outside Simulator zone, EMF vanilla fish, bait/treasure, competition and animations unchanged.
- [ ] G3-02: Cast inside zone, stand outside zone; verify hook location routing and intended viewer visibility.
- [ ] G3-03: Test `PlayerFishEvent.FISHING` canceled at LOWEST; no Simulator session.
- [ ] G3-04: Native `BITE` cannot grant catch while Simulator owns the session.
- [ ] G3-05: Native `CAUGHT_FISH`/loot/EXP cannot escape owner probe after reel/recast or session-close tombstone.
- [ ] G3-06: Event cancellation by protection plugin before HIGHEST; no bypass.
- [ ] G3-07: Rapid reel, offhand, rod swap, creative/spectator, cross-zone/cross-world, stale phao; no duplicate reward.
- [ ] G3-08: Native hook control API behavior verified on every target Paper build, not inferred from javadoc alone.

## C. G5 packet fish and viewer lifecycle

- [ ] G5-01: COD, SALMON, TROPICAL_FISH, PUFFERFISH spawn, move and despawn on actual Minecraft clients.
- [ ] G5-02: 2 clients see same SHARED fish position and behavior; PRIVATE fish visible only to owner.
- [ ] G5-03: Spectators do not receive private fish unless expressly authorized.
- [ ] G5-04: Destroy-first during heavy viewer churn; no stale packet fish after leave zone/world, permission revoke, quit, reload.
- [ ] G5-05: Deliberately disconnect during spawn and before metadata; verify best-effort cleanup and required relog recovery.
- [ ] G5-06: PacketEvents backend returns false/throws; error count increments, emergency off triggers if appropriate, no repeated console flood.
- [ ] G5-07: No Bukkit entities for visual school; zero forced chunk loads from water scanning.
- [ ] G5-08: Invalid/missing resource pack falls back to vanilla fish; custom ITEM_DISPLAY still not implemented.
- [ ] G5-09: Async packet callback never reads/modifies Bukkit entity/world state on incorrect thread.

## D. G7 fish identity, reward consistency and economy

- [ ] G7-01: Displayed `FishKey` = awarded EMF fish rarity/ID in >1000 successful trials.
- [ ] G7-02: Correct size, item metadata, shop value, journal and competition history after success.
- [ ] G7-03: `EMFFishCaughtEvent` canceled: 0 item, EXP, bait cost, stat record, competition entry.
- [ ] G7-04: Two players fight over shared fish: exactly one possible successful result.
- [ ] G7-05: Spam double reel, packet spoof, reentrant reload: never double award.
- [ ] G7-06: Custom rod offhand/swap/allowed rarity; bait with custom rod; fractional region multipliers 0.5 and 1.8.
- [ ] G7-07: Inventory full and canceled item drop: no dupe, no lost/stat-inconsistent rewards. **Known unverified risk; fail release if it occurs.**
- [ ] G7-08: SQLite/MySQL timeout, unavailable DB, shutdown during commit: no synchronous server tick blocking or wrong durable stats.
- [ ] G7-09: Default bait loot, native treasure and unsupported external loot rules preserve legacy behavior.
- [ ] G7-10: External reward command failure does not trigger replay of the entire fish delivery.

## E. Performance & resource soak

For each load tier `1, 10, 30, 50, 100` actual anglers, record Paper exact version, host CPU, heap limit, world/chunk counts, players online, packet counts, network traffic, peak/median/p95 MSPT, GC pause, fish/visible/viewer/session counts before/after, and 30-minute soak. **The current admin-only implementation caps preview viewers at 32 and AI sessions at 16, so 50/100 active Simulator gameplay is NOT yet supported. Do not claim these tiers passed.**

- [ ] PERF-01: Baseline legacy EMF + PacketEvents disabled.
- [ ] PERF-02: Admin fish ambient preview 1/10/30 authorized viewers; compare disabled/enabled.
- [ ] PERF-03: Stress 50/100 active anglers **after public engine path and cap design exist**.
- [ ] PERF-04: Repeated join/quit/teleport/reload/permission changes: no growth in UUID maps, virtual entity tracking, cache, water queue, offheap allocation.
- [ ] PERF-05: No packet cap overshoot under simultaneous spawn/move/destroy + cinematic; document that cinematic has its own separate budget.
- [ ] PERF-06: Destroy backlog returns to zero after load relief; no client ghost requiring relog in normal operation.
- [ ] PERF-07: spark profiling of main tick, water scan and region API; fail on reproducible harmful TPS/MSPT regression.
- [ ] PERF-08: Packet serialization/latency measured on real clients (mock transport benchmark only measures Java logic).

## F. Safe rollout decision

- [ ] All P0/P1 defects closed and retested.
- [ ] Build & staging logs attached and reproducible.
- [ ] G3, G5, G7 signed off by tester with evidence.
- [ ] Rollback dry run and backups verified.
- [ ] Public-player Simulator path actually implemented (currently **NO**).
- [ ] Operators acknowledge PacketEvents/Folia/pack/resource limitations.

**Gate G9: FAIL / NOT ASSESSED until every necessary box is verified.**
