# Phase 06 — Paper + PacketEvents validation matrix (manual; NOT run)

> Target: Paper 1.21.4+ (existing variant compiled against 1.21.1 API); Java 21; PacketEvents runtime. **Staging server only**. Never infer PASS from the headless JVM smoke tests.

## Setup

1. Run full Gradle compile/shadowJar and install the **1.21 variant** alongside PacketEvents on a clean test server.
2. Configure one test lake region in `simulator.yml`; set `simulator.enabled: true`, `ambient-preview.enabled: true`, `native-hook-probe.enabled: true`, `ai-preview.enabled: true`.
3. Use two admin accounts with `emf.admin.simulator.probe` + `emf.admin.simulator.preview`, and one normal player without either permission.
4. Set `on-unavailable: LEGACY_EMF` and keep a backup of production data/config. Verify test lake chunks are loaded.
5. Record spark profile, `/emf admin simulator trace` and client packets. Stop on any item/EXP in a preview catch.

## Functional validation

| ID | Scenario | Expected |
|---|---|---|
| G6-01 | Throw rod while standing inside Simulator zone, bobber lands inside | Admin probe owns hook after water contact; AI session begins |
| G6-02 | Player stands outside, bobber lands inside zone | Zone selected by bobber; does not trust player's original position |
| G6-03 | Bobber lands outside | Legacy EMF handling only; no AI session |
| G6-04 | Hook is flying / stuck in land | No AI reservation until bobbing in water |
| G6-05 | Fish is already swimming in indexed water | Fish transitions INTERESTED → APPROACHING → BITE_READY |
| G6-06 | Visible fish arrives by adjacent water cells | Packet movement smooth; no jumping through blocks/land |
| G6-07 | Dry gap between fish and bobber | No path/teleport across dry gap |
| G6-08 | Path crosses higher-priority DENY zone | No path crosses DENY zone or different world |
| G6-09 | Two admin players target same shared fish | One reservation at a time; second cannot claim same fish |
| G6-10 | Admin A has private fish; admin B fishes nearby | Admin B neither sees nor reserves Admin A's private fish |
| G6-11 | Reel while SEARCHING / INTERESTED / APPROACHING | EARLY_REEL; no item/EXP; reservation cleaned; fish flees |
| G6-12 | Reel within 26-tick response window | SUCCESS_PREVIEW_ONLY; virtual fish removed; no reward/EXP |
| G6-13 | Reel at last grace tick | One preview success; no duplicated state |
| G6-14 | Reel after grace window | MISSED_WINDOW; no item/EXP; fish released and flees |
| G6-15 | Do not reel after bite | Auto miss and cleanup; hook removed; no reward |
| G6-16 | Spam right-click / rapid duplicate terminal events | Exactly one terminal result per hook UUID |
| G6-17 | Cast switch main-hand → offhand / replace rod | Session cancelled; fish released; no reward |
| G6-18 | Lure/enchanted rod with native bite suppression | No native item or EXP before/after AI bite; check G3 trace |
| G6-19 | Other plugin cancels PlayerFishEvent | AI does not award or persist and cleans up safely |
| G6-20 | WorldGuard / RedProtect permission denial | Fail closed, cannot bypass protected region |
| G6-21 | MMOItems/custom rods with allowed-fish whitelist | Ineligible rarity/species are never attracted |
| G6-22 | Bait-boosted rod, bait-usage NBT before/after preview | Attraction may differ; bait charge is NOT consumed |
| G6-23 | Fish requirements (biome/world/time/permission) change | New targeting rejects fish; active session closes on invalid EMF policy |
| G6-24 | Chunk unload mid-approach | Fish is removed/reservation cleaned; no phantom entity |
| G6-25 | Change zone/world/teleport while baited | Native hook/AI/packet state cleaned |
| G6-26 | Player quits or reconnects in bite window | Session/ID cleaned, no virtual fish leak |
| G6-27 | Simulator reload during approach | Old session aborted, config version changed, no stale callbacks |
| G6-28 | `/emf admin simulator off` while active | All hooks and reservations cleaned; normal EMF continues |
| G6-29 | Invalid ai-preview config ranges | Reload rejects candidate and keeps last valid snapshot |
| G6-30 | PacketEvents disabled while admin preview running | Visual and AI stop, legacy EMF available, no hard plugin crash |
| G6-31 | Normal player without probe/preview permissions catches fish | Legacy EMF reward, journal, shop, competition unchanged |
| G6-32 | Spawn 30–100 viewers with large lakes | Packet count and MSPT acceptable by profiler (record actual values) |
| G6-33 | Relog / reload / unload repeatedly (10+ cycles) | No growth in session, viewer, entity IDs or leaked tasks |
| G6-34 | Real native `CAUGHT_FISH` accidentally fires during probe | Event/EXP blocked; immediately fail closed; G3 requires verified evidence |

## Release gates

- [ ] `gradlew test shadowJar` passed with resolved dependencies.
- [ ] G3 observed: **zero native item and EXP** from an owned Simulator preview session.
- [ ] G5 observed: Packet fish spawn, move, despawn and private visibility work on client.
- [ ] G6 observed: Hook → fish approach → bite → reel and all failure branches work in live Paper.
- [ ] Full legacy EMF smoke regression passes outside zones.
- [ ] Any plugin conflict/error disables only Simulator preview, not the legacy EMF features.
- [ ] G7 reward commit implementation and transactional tests are completed **before** enabling production Simulator.
