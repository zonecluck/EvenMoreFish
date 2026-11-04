# Phase 9.1 — Paper staging verification checklist (not yet executed)

## Environment and build

- [ ] Build on Java 21 with Gradle 9.5.1: `./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon`
- [ ] Record full stack trace of any compilation error and correct the root cause (especially optional PacketEvents and Paper method signatures).
- [ ] Verify only one EMF JAR matching Paper version is installed; PacketEvents 2.14.0 installed and enabled.
- [ ] Preserve original EMF `config.yml`/fish definitions; simulator config disabled by default; migration must not rewrite user config.

## G3 — Native hook control (REQUIRED before enabling reward bridge)

- [ ] Enable admin probe only in an isolated test region. Outside zone must remain 100% legacy EMF.
- [ ] Place bobber in air/land/edge/border water; takeover only after hook settled and zone confirmed.
- [ ] Test native `FISHING`, `BITE`, `REEL_IN`, `FAILED_ATTEMPT`, `CAUGHT_FISH`, `CAUGHT_ENTITY`, `IN_GROUND` event ordering with verbose trace.
- [ ] Confirm no vanilla item, bait loot, treasure or native EXP escapes a simulator-owned hook, including when another plugin cancels or mutates fish event.
- [ ] Verify offhand, rod swap, player disconnect, teleport, zone exit, plugin reload and emergency OFF close owned hooks without double reward.

## G5 — Live PacketEvents rendering

- [ ] With 2 Java clients observe COD/SALMON/TROPICAL_FISH/PUFFERFISH, shared and private fish visibility and correct smooth movement.
- [ ] Confirm invalid/unloaded water chunks remove fish without force-loading.
- [ ] Inject / simulate a metadata-send failure after spawn if staging tools permit; next owner tick must attempt destroy; no replacement spawn until cleanup.
- [ ] Test PacketEvents disable/unload while preview active. `doctor` must report failures/pending destroys; reload must not falsely declare success if IDs remain.
- [ ] Test relog, world change, reload, emergency-off and plugin disable while cinematic fish is visible. Check client/entity cleanup.
- [ ] Monitor real `spark` MSPT and packet throughput with 5/20/50 active viewers. Synthetic 100-viewer benchmark is not sufficient.

## G7 — Catch, reward and durability

- [ ] Verify displayed FishKey == caught fish id/rarity/metadata/length/price in EMF journal, shop and leaderboard.
- [ ] Cancel `EMFFishCaughtEvent` with a test addon: zero reward, zero bait consume, zero internal catch history.
- [ ] Compare mainhand/offhand and normal/custom rods, bait attraction/use, EXP and double-catch to the legacy engine.
- [ ] Test full inventory (whole-stack drop), partial matching stack that can hold the entire reward, no compatible single slot, drop cancelled by protection plugin.
- [ ] Rapid double reel and multiple simultaneous anglers on the same shared fish: one winner maximum.
- [ ] Test quit/reload while synchronous commit event handler runs, DB outage, restart/rejoin and shutdown durability.
- [ ] Record any case where a third-party addon gives side-effect rewards during a cancellable event: this is NOT covered by exactly-once internal item delivery.

## Release gate

- [ ] Save actual Paper server log, version, PacketEvents version, client observations, profiler capture and reproduction steps.
- [ ] Gate owners explicitly sign off G3/G5/G7; do NOT infer sign-off from `doctor` command or from any config boolean.
- [ ] Test emergency-off rollback in same staging environment and re-check vanilla EMF competitions, bait, journal and old fish items.
