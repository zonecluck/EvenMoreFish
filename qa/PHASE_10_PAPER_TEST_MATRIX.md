# Phase 10A: Real Paper / HUD regression acceptance matrix

**Environment required:** actual Paper 1.21.4+ runtime, compatible PacketEvents, client screenshots/video. The list below is NOT marked PASS by headless tests.

## Baseline/release blocking

- [ ] Gradle full plugin/test build succeeds (no stub compilation).
- [ ] First enable creates `simulator/presentation.yml`, validates config-version, all Simulator features remain OFF.
- [ ] Legacy EMF catch (fish item, rarity, bait, rod, competition, journal, shop) unchanged outside Simulator zone.
- [ ] Invalid `presentation.yml` key or unknown profile rejects reload and retains previous valid snapshot.
- [ ] G3: Native FishHook never duplicates item/EXP in admin probe.
- [ ] G5: Packet fish spawn, teleport, metadata and destroy render/clean up on client.
- [ ] G7: EMF reward/event/DB history consistent for success, cancel, inventory full, quit and reload.

## HUD behavior (compare before/after screenshots)

- [ ] At INTERESTED, there is NO centered ActionBar spam by default.
- [ ] At BITE_READY, exactly ONE ActionBar shows a readable bar, seconds and right-click instruction.
- [ ] ActionBar refresh stays within configured tick interval, without duplicate title/subtitle.
- [ ] Earlier hint never overwrites bite countdown.
- [ ] Catch success preempts countdown immediately and remains visible after hook/session teardown.
- [ ] Reel failure, missed window and no-target cases display the correct localized result.
- [ ] New cast cancels stale result and stale delayed callbacks cannot overwrite new cast.
- [ ] Quit/rejoin and world change clean all HUD state; no ghost cinematic packet entity remains.
- [ ] In a dark underwater lake and with shaders/resource pack off, HUD is legible at 720p and 1080p.
- [ ] Coexistence with external scoreboard/ActionBar/HUD plugins: choose a non-conflicting channel or disable Simulator ActionBar when necessary (cannot forcibly own other plugins' ActionBar).

## Preferences and safety

- [ ] `/emf hud` and each `/emf hud <profile>` enforce player-only and `emf.simulator.preferences`.
- [ ] Profile persists across relog/restart; no synchronous file I/O on PlayerJoin or command handler.
- [ ] OFF suppresses Simulator ActionBar/particles/cinematic, but never disables fishing gameplay.
- [ ] MINIMAL allows bite sound but suppresses particle/cinematic; REDUCED_MOTION remains readable.
- [ ] Switching OFF while a packet cinematic is running destroys its virtual entity.
- [ ] Invalid/oversized preference file logs one clean error and falls back; no crash or console spam.
- [ ] Rapid profile changes (50 clicks) end in last selection; durable saved profile matches the last selection.
- [ ] Disabling plugin flushes preferences or warns when flush exceeds timeout.
- [ ] `/emf admin simulator status` includes presentation diagnostics without exceptions.

## Required production measurements

- [ ] Compare MSPT/packet counts for 1, 10, 30 and 50 real viewers (no assumption from mock tests).
- [ ] Test GUI safety if Phase 10B HUD GUI is later implemented: raw slots, bottom clicks, drag, shift, double-click, number key, offhand, close/reopen/relog.
