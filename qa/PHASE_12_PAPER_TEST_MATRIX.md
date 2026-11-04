# Phase 12 — Paper & Minecraft Client Acceptance Matrix

> All runtime boxes intentionally left unchecked: no Paper staging client is available in the build environment. G3/G5/G7 must be tested separately.

## Gate P12-A: configuration, lifecycle & safety

- [ ] A1. Boot Paper 1.21.4+ with Java 21, EMF and PacketEvents; no errors on enable.
- [ ] A2. `simulator/schooling.yml` auto-generates and retains admin comments.
- [ ] A3. Defaults OFF preserve all vanilla EMF fishing, bait, rarity and competitions.
- [ ] A4. Enable schooling in a staging-only lake; outside the zone use legacy EMF.
- [ ] A5. Changing a number outside its configured range fails reload without replacing active snapshots.
- [ ] A6. Invalid YAML fails reload without partial application.
- [ ] A7. Turn `schooling.enabled=false` and reload; fish return to solo behavior, stale school state gone.
- [ ] A8. `/emf admin simulator off` destroys all visible fish without ghosts after reconnect.
- [ ] A9. Reload with a fish reserved for AI; reservation is closed safely, no reward/item duplication.
- [ ] A10. Disable/enable plugin, player quit, world change and respawn leave no tracked ghost fish.

## Gate P12-B: movement & groups

- [ ] B1. 2–6 fish form a visually coherent school, no new entity count beyond population caps.
- [ ] B2. Leader swims between connected water cells; others follow with staggered offset.
- [ ] B3. School turns gradually at open-water bends; client yaw does not snap wildly.
- [ ] B4. Narrow 2-block canal still behaves safely (no walking onto land or waterless blocks).
- [ ] B5. An agent does not cross blocked WorldGuard/RedProtect/denied Simulator boundaries.
- [ ] B6. Chunk unload near leader causes safe removal, not an infinite floating model.
- [ ] B7. Reserving a fish detaches it; the fish swims to the hook under AI authority.
- [ ] B8. AI early-reel, miss and success do not result in two systems fighting over motion.
- [ ] B9. Successful reel triggers nearby fish to scatter and then regroup, without any bonus loot.
- [ ] B10. When the leader is removed, remaining school elects another or disperses cleanly.
- [ ] B11. A salmon model is not grouped into a cod-model school; no model flashing.
- [ ] B12. Two PRIVATE schools for different owners never leak to another client's viewer list.
- [ ] B13. Admin can preview shared school while a non-preview player sees none (current staging policy).

## Gate P12-C: performance, visuals & regression

- [ ] C1. PacketEvents spawn+metadata, teleport yaw, destroy work across connect/reconnect.
- [ ] C2. Near/middle/far viewers observe stable existing packet LOD without orientation jitter.
- [ ] C3. Artificial packet transport failure retains cleanup state; no duplicate entity IDs.
- [ ] C4. Confirm max agents/max per zone/max visible per viewer/800 packet budget never exceeded.
- [ ] C5. Record Paper profiler MSPT at 1/10/30/50+ players; no new >2ms sustained preview spikes.
- [ ] C6. Simulate 100 shared viewers in staging if feasible; collect total packet throughput.
- [ ] C7. Water index bounded under BlockFromTo/waterfall updates, no force chunk loading.
- [ ] C8. Full Gradle compile + JUnit + Paper module shadowJar PASS (release blocker).
- [ ] C9. Existing G3 native loot suppression PASS (release blocker).
- [ ] C10. Existing G5 real-client rendering PASS (release blocker).
- [ ] C11. Existing G7 reward/DB/competition consistency PASS (release blocker).

**Release state until all blocking cases pass:** admin-only schooling preview; no production Simulator rewards.
