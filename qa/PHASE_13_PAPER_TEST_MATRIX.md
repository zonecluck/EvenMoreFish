# 🧪 Phase 13 — Paper Staging / Manual Acceptance Matrix

> All boxes start unchecked. Domain/JVM smoke tests do **not** certify Minecraft runtime behavior.

## Required installation and boundaries

- [ ] Build and run with real Paper (1.21.4+) + Java 21 and compatible PacketEvents.
- [ ] Verify startup with ecosystem/FX disabled: previous EMF catching works without behavior changes.
- [ ] Verify invalid ecosystem YAML rejects reload atomically; old active config remains.
- [ ] Uninstall PacketEvents or disable preview: no crash/no leaked fish/reward path.
- [ ] Do not mark Folia support; no region-scheduler implementation here.

## Environment snapshots

- [ ] In a shallow river, sampled depth changes only when the cache expires/invalidates.
- [ ] Deep lake: `DEEP_DWELLER` chooses valid lower indexed water cells, never land.
- [ ] At day/night transition, `NOCTURNAL` speed changes **after bounded snapshot refresh**.
- [ ] Raining/thunder/clear: animation responds without modifying item/rarity eligibility.
- [ ] Biome edge crossing: only ambient differences; no compatibility regression with EMF requirements.
- [ ] Place/break blocks, empty/fill bucket, water flow and chunk unload: invalidation works.
- [ ] Unloaded chunks must not be force-loaded by environment sampling or pathfinding.

## Personality and visuals

- [ ] Shared `SHY` fish flee valid water cell when player causes disturbance; reserved fish cannot be stolen.
- [ ] Private fish do not flee in reaction to another player's cast/reel.
- [ ] A fish in `APPROACHING` and `BITE_READY` remains controlled by `FishAiDirector`.
- [ ] Bubble trail/ripple visible only after underlying packet fish appeared on viewer client.
- [ ] Lure and bite cues only appear for the casting player in the correct zone/world.
- [ ] OFF, MINIMAL, BALANCED, CINEMATIC, REDUCED_MOTION settings respected.
- [ ] Environmental source failure does not cancel existing catch session; no console spam.
- [ ] Quit, relog, teleport, dimension change and reload remove pending FX/session state.

## Performance and release gates

- [ ] At 30+ real client viewers, check actual packet output, FPS, ping and MSPT with profiler.
- [ ] Track `samples`, `sample-drops`, `fx-drops`, memory/cache bounds via status.
- [ ] Particle effects do not hide the HUD or cinematic reveal when a fish bites.
- [ ] G3 — native loot/XP blocking verified without double rewards.
- [ ] G5 — packet fish spawn/movement/cleanup verified on supported client versions.
- [ ] G7 — event cancellation, bait/rod, fish identity, DB, reward once verified.
- [ ] Shutdown/reload/PacketEvents outage causes no lingering ghost fish or pending effect leak.

## Suggested release decision

**FAIL CLOSED** if G3/G5/G7 or full Gradle compilation are unverified. Keep
`simulator.enabled: false`, `reward-bridge.enabled: false`, `ecosystem.enabled: false`
on production until staging evidence is collected.
