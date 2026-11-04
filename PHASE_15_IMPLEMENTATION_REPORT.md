# 🎣 EvenMoreFish Simulator V2 — Premium Phase 15 Implementation Report

**Baseline:** `EvenMoreFish-Simulator-Premium-Phase14-source.zip`  
**Target:** Paper 1.21.4+ / Java 21 / PacketEvents 2.14.0  
**Scope:** packet `ItemDisplay` custom fish visuals with strict viewer-specific resource-pack acknowledgement.  
**Status:** SOURCE IMPLEMENTED + HEADLESS REGRESSION VERIFIED; **NOT** a production JAR.

## 1. Implemented in Phase 15

- `FishRenderSpec`: immutable description of vanilla fallback vs `minecraft:item_model` display, material, scale and interpolation, strict key validation.
- `FishModelSettings` + `FishModelConfigLoader`: `simulator/models.yml` with exact `Rarity:Fish Name` overrides and rarity defaults; validated UUID, item `Material`, namespaced model key, scale and interpolation.
- `FishPackAudience`: only `PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED` **for the same pack UUID** allows custom display for that player; disconnections revoke approval.
- `FishRenderPolicy`: per-viewer selection; defaults to existing vanilla cod/salmon/tropical fish/pufferfish; rejects unsupported clients and absent models.
- `PacketTransport` and `PacketVisibilityService`: per-viewer render selection and **destroy-old-before-spawn-new** when pack/model changes. Existing orphan-destroy ledger, packet budget, visibility and privacy rules remain.
- `Paper121PacketEventsTransport`: uses `EntityTypes.ITEM_DISPLAY`, `EntityDataTypes.ITEMSTACK`, `VECTOR3F` and `minecraft:item_model` item component (PacketEvents API 2.14.0). One packet ID per fish **per viewer**, not one Bukkit mob per player. Uses preexisting teleport packet and `EntityDataTypes` metadata.
- `Paper121PackStatusListener`: listener only in Paper 1.21 variant, with quit cleanup; core remains independent of Paper's resource-pack API.
- `PacketCatchCinematicService`: custom render spec for the existing visual-only catch fish; aborts cinematic if pack authorization changes and retains partial spawn cleanup.
- `simulator/models.yml`: ships **disabled** with both override maps explicitly `{}`. Fixed a default-config issue where an all-commented `fish-overrides:` would have parsed as YAML `null`.
- A minimal **example resource pack asset structure** at `docs/simulator/resource-pack-example/`. The supplied texture is the vanilla cod placeholder, not finished artwork.
- Admin model stats integrated into `/emf admin simulator status` via locale keys, including Vietnamese.

## 2. Important contracts

1. **Visuals do not select fish.** `FishKey`, rarity, EMF fish item, catch weight, bait, competition and reward pipeline are unchanged.
2. **Viewers differ:** the same shared agent can be an `ItemDisplay` for viewer A and a vanilla salmon for viewer B (who has not loaded the exact pack ID).
3. **Pack UUID must be the ID sent by the actual pack delivery service**, not just a UUID typed into the YAML; configuring a nonexistent ID will correctly leave everybody on vanilla fallback.
4. **Packet-aware only:** no NMS, no world entity spawn, no new chunk loads.
5. **Private fish is still private:** model selection is downstream of the existing per-viewer fish visibility authorization.
6. **Feature flags OFF by default:** `simulator.yml`, `ambient-preview`, `reward-bridge`, `models.yml` and Premium visual systems are not enabled by this update.
7. **Not Folia-ready:** Paper owner-thread assumptions remain from the baseline.

## 3. Changed files and files added

Core new package: `even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/simulator/model/`

- `FishRenderSpec.java`
- `FishModelSettings.java`
- `FishPackAudience.java`
- `FishRenderPolicy.java`
- `FishModelConfigLoader.java`

Core modifications: `SimulatorModule.java`, `AmbientPreviewService.java`, `PacketTransport.java`, `PacketVisibilityService.java`, `PacketCatchCinematicService.java`, `SimulatorEffectsService.java`.

Paper 1.21 modifications: `Paper121PacketEventsTransport.java`, `Paper121PackStatusListener.java`, `EMFModule.java`, `SimulatorSubcommand.java`.

Resources: `simulator/models.yml`, 18 locale YAMLs, example pack assets, QA harness and docs.

## 4. Validation actually performed

- Java 21 syntax parser: **493 Java source files**, no syntax errors detected (not a full project compile).
- Regression `qa/run-premium-phase14-validation.sh`: **PASS Phase 01–14** after updating the focused compilation scripts to include new model class dependencies.
- Phase 15 domain smoke: PASS exact pack ACK, unsupported adapter fallback, shared/private viewers, dynamic vanilla/custom swaps, destroy failure and retry, model key validation.
- Actual `FishModelConfigLoader` compiled **with minimal Bukkit API stubs**: PASS validation of incorrect UUID, boolean, YAML mapping, material, model ID, floating point range, interpolation.
- YAML parse/locale validation: see `qa/PHASE15_VALIDATION_LOG.txt`.
- `qa/PHASE15_GRADLE_ATTEMPT.log`: **FAIL BEFORE COMPILE** due `UnknownHostException: services.gradle.org` while downloading Gradle 9.5.1. Neither full Gradle/JUnit execution nor Paper runtime verified.

## 5. Remaining blockers and limitations

| Gate | Status | Required on staging |
|---|---|---|
| Gradle API/bytecode compile | **UNVERIFIED** | Compile against real Paper 1.21.1+ and PacketEvents 2.14.0 jars; repair any type/API errors |
| G3 — Native hook no-double-loot | **UNVERIFIED** | Verify vanilla EXP/drop suppression on live Paper |
| G5 — Packet fish client visuals | **UNVERIFIED** | 1.21.4/1.21.11 client with same pack UUID; inspect display orientation/metadata |
| G7 — Reward consistency | **UNVERIFIED** | Catch/item/event/competition/inventory-full integration |
| G15 — Custom item model | **UNVERIFIED** | Test pack accepted/declined/delayed/replaced, viewers mixed versions, join/quit/reload |
| Finished model art | **NOT PROVIDED** | Author actual `.json`/texture or 3D meshes in resource pack (example only) |

Do not activate as a public simulator or call this build production-ready until these gates are passed.

## 6. Hand-off recommendations

1. On a machine with Gradle/dependency access, run `./gradlew :even-more-fish-plugin-1-21:compileJava :even-more-fish-plugin-1-21:test --no-daemon`.
2. Verify PacketEvents version and resource-pack UUID delivery from your current pack manager.
3. Set up one admin-only region; first confirm a single custom fish with a real `ledat:fish/...` resource-pack model.
4. Verify fallback separately for a player who declines the pack, then enable only the staging preview.
5. Continue Phase 16 production checks before releasing, not directly to live players.
