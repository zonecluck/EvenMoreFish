# EvenMoreFish Simulator V2 — Premium Phase 14 Implementation Report

**Build state:** STAGING SOURCE ONLY. No compiled Paper plugin/JAR delivered. No G3/G5/G7 sign-off.

## Scope

Phase 14 implements the optional **Rare Encounter Director** on top of the Phase 13 population,
Fish School, EnvironmentSampler, PacketVisibilityService and Phase 11 Cinematic infrastructure.
It does **not** create new fish entities, alter FishKey/rarity sampling, introduce rewards, or
force a catch. Every encounter references already existing `FishAgent` UUIDs.

### New classes/resources

- `encounter/RareEncounterDirector.java`: bounded cosmetic encounters, weighted selection,
  per-fish one-time roll, zone/private cooldowns, visibility-aware observation, expiration,
  commit notification and metrics.
- `encounter/RareEncounterSettings.java`: immutable typed settings, strict budget validation,
  rarity whitelist and exact `Rarity:FishId` overrides.
- `encounter/RareEncounterConfigLoader.java`: versioned YAML loader with fail-closed candidate parsing.
- `resources/simulator/encounters.yml`: disabled-by-default, commented settings and 5 presets.
- `qa/SimulatorPremiumPhase14Smoke.java` and `qa/SimulatorPremiumPhase14ConfigSmoke.java`;
  `qa/run-premium-phase14-smoke.sh` and `qa/run-premium-phase14-validation.sh`.
- `src/test/java/.../encounter/RareEncounterDirectorTest.java`: JUnit tests added for future real Gradle runs.

### Existing classes changed

- `AmbientPreviewService`: one owned encounter runtime, render only to viewers with a live virtual
  fish (`PacketVisibilityService.shownTo`), only in the correct world, zone and privacy scope;
  strictly bounded `Particle` emission, cleanup on quit/world change/reload; handles staff preview.
- `SimulatorModule`: atomic candidate loading, diagnostics, preview bridge, and successful
  `CatchCommit` correlation by exact fish UUID. Visuals do **not** modify the commit result.
- `FishSchoolService`: safe immutable `membersOf(UUID)` snapshot; no mutable group exposed.
- `SimulatorSubcommand`: `/emf admin simulator encounter preview <kind>`, status metrics.
- 18 EMF locale files: diagnostics, invalid type, admin preview feedback and unavailable reasons.

## Encounter types (pure cosmetics)

| Kind | Visual cue | Requirements (configurable) |
|---|---|---|
| RARE_PRESENCE | Small `END_ROD` orbit | Existing whitelisted rarity |
| GOLDEN | Gold `DUST` ring | Existing whitelisted rarity |
| SHADOW | `SQUID_INK` trail | `DEEP` EnvironmentSnapshot |
| GHOST | `SOUL` sparkles | `NIGHT` EnvironmentSnapshot |
| RARE_SCHOOL | `ENCHANT` rotating over existing members | Real school of 2–4 eligible fish |

Exact `Rarity:FishId` overrides choose the cosmetic type only. They cannot create an otherwise
ineligible fish and cannot change selection/loot weights. The Particle enum renames between the
legacy Paper 1.20.1 core compile target and 1.21.4 runtime are resolved by safe one-time fallback.

## Safety and performance

- Shared and PRIVATE fish remain in the existing population and obey the original caps.
- PRIVATE encounter never emits to a different owner; world/zone and packet visibility rechecked.
- Up to 8 live encounters globally, 2 per world/zone (both scopes combined by default).
- At most 20 candidates per 20 ticks; each eligible fish rolls at most once; no per-tick loot reroll.
- Distinct zone cooldown for the privacy scope, plus first-viewer reveal cooldown.
- Emitter failures use bounded retry at the configured interval; always consume the FX budget.
- Particle budget defaults to 24/tick and 6/viewer/tick for encounter FX, independent of ambient FX.
- On expiration/removal/reload/quit/world change, all references are forgotten; no new virtual
  entity IDs are allocated and no DB rows are created by this feature.
- Stale client-visible fish cleanup remains owned by the existing `PacketVisibilityService`.
- `COMPLETED` metrics only count successful `SUCCESS_COMMITTED`, never preview-only reels.

## Verification performed

- Real Java 21 `javac` compilation of Phase 14 pure-domain classes and YAML loader against
  small API stubs: **PASS**.
- Phase 14 executable smoke tests: rarity/FishKey, one-time roll, forced FishKey override,
  night/deep restrictions, school membership, private owner isolation, packet-visibility guard,
  reservation authority, commit/expire cleanup, 100 synthetic viewers, budgets: **PASS**.
- Phase 01–13 headless regression, plus Phase 14 smoke: **PASS** via
  `bash qa/run-premium-phase14-validation.sh`.
- Java 21 parser across all source files: **PASS** (see validation log).
- YAML resources and 18 locale files: **PASS**. Flags are **OFF** by default.

### Unverified / release blockers

- **Full Gradle compile/JUnit:** BLOCKED before project compilation because DNS cannot resolve
  `services.gradle.org` to download Gradle 9.5.1 (see `qa/PHASE14_GRADLE_ATTEMPT_LOG.txt`).
- **G3** native `FishHook` loot/EXP suppression on live Paper: NOT VERIFIED.
- **G5** PacketEvents client-side fish rendering/cleanup and encounter FX visibility: NOT VERIFIED.
- **G7** actual reward/item/competition/database invariants on Paper: NOT VERIFIED.
- No client screenshots/videos or Paper MSPT measurements were produced.
- The Phase 14 visual overlay has not been visually tuned on a running Minecraft client.

## Follow-up

1. Build with actual Gradle/Paper libraries. Resolve compile errors and rerun JUnit if any.
2. Test G3/G5/G7 in a dedicated staging world with PacketEvents; record evidence.
3. Enable `simulator/encounters.yml` **only after** the existing admin ambient preview is validated.
4. Measure 1/10/30/50/100 viewers, particle+ambient packet budget, client FPS and cleanup.
5. Only after all gates pass, consider Phase 15 custom `ITEM_DISPLAY` resource-pack models.
