> **Ordinary EMF fishing (2026-10-12):** Persistent admin Fishing Pools and private rare-catch cinematics run independently of Simulator staging flags. See [Vietnamese admin guide](docs/fishing-pools-and-cinematics.md) and [local verification with runtime limits](plans/20261012-vanilla-cinematics-fishing-pools/reports/final-verification.md).

> 🐠 **Simulator Premium Phase 12 (STAGING / admin preview only):** Bounded shared/private fish schools, leader/follower motion, staggered-V formation, smooth packet yaw, scatter/reform, water+region guards, and new `simulator/schooling.yml` (OFF by default). No fish/reward changes. See [`PHASE_12_IMPLEMENTATION_REPORT.md`](PHASE_12_IMPLEMENTATION_REPORT.md), [`docs/simulator/PHASE_12_FISH_SCHOOLING_ADMIN_GUIDE.md`](docs/simulator/PHASE_12_FISH_SCHOOLING_ADMIN_GUIDE.md), and `bash qa/run-premium-phase12-validation.sh`. **Full Gradle compile and Paper G3/G5/G7 remain unverified.**

> **Simulator Premium Phase 10B/11 (staging):** This source snapshot adds a configurable HUD preset GUI and visual-only multi-stage rarity cinematics. Both are OFF by default. See [`PHASE_10B_11_IMPLEMENTATION_REPORT.md`](PHASE_10B_11_IMPLEMENTATION_REPORT.md) and [`docs/simulator/PHASE_11_PREMIUM_ADMIN_GUIDE.md`](docs/simulator/PHASE_11_PREMIUM_ADMIN_GUIDE.md). **Full Gradle compilation and Paper G3/G5/G7 remain unverified.**

> 🎣 **Premium Phase 10A (historical stage):** Unified Simulator ActionBar + `/emf hud` presets + async UUID preference files. See `PHASE_10_IMPLEMENTATION_REPORT.md`. GUI and multi-stage cinematic were subsequently implemented at source level in Phase 11; full Gradle/Paper gates remain **unverified**.

> **Simulator Phase 9.1 staging:** packet-orphan retry + single-slot catch delivery hardening. Not a production release; see `PHASE_09_1_IMPLEMENTATION_REPORT.md` and `qa/PHASE_091_PAPER_TEST_MATRIX.md`.


## 🎣 EvenMoreFish Simulator V2 — Phase 09 cumulative staging snapshot

This branch contains **Phase 01–09 source changes**: an opt-in `simulator.yml`, region routing and native-hook probes, shared/private packet fish, water indexing, attraction/Bite AI, admin-only EMF reward bridge, cinematic FX and Phase 9 performance hardening. **All Simulator/reward/effects features default OFF. Normal users still use legacy EvenMoreFish**; this is NOT a public Simulator or production-ready JAR.

- **Latest implementation notes:** [`PHASE_09_IMPLEMENTATION_REPORT.md`](PHASE_09_IMPLEMENTATION_REPORT.md)
- **Operator runbook & rollback:** [`docs/simulator/PHASE_09_ADMIN_OPERATIONS.md`](docs/simulator/PHASE_09_ADMIN_OPERATIONS.md)
- **Required Paper tests:** [`qa/PHASE_09_PAPER_TEST_MATRIX.md`](qa/PHASE_09_PAPER_TEST_MATRIX.md)
- **Headless tests:** `bash qa/run-phase9-regression.sh` (production classes, mocked transport)
- **Production blockers:** full Gradle compile, Paper G3 native loot suppression, G5 PacketEvents client rendering and G7 reward/DB semantics **NOT VERIFIED**. No Folia support claim.

# 🐠 EvenMoreFish Paper Plugin 🐟

Improve your server's fishing experience with an incredible **fishing competition plugin** based on elsiff's MoreFish
plugin

With over 60 custom fish in the default configurations, and the ability to add your own, this is the best competition
plugin for your server.

---

Supports 1.20.1+

If you encounter any issues with the plugin, please do the following before reporting the problem:
- Create a Paper test server using one of the supported versions.
- Add the latest [dev build](https://ci.codemc.io/job/EvenMoreFish/job/EvenMoreFish/) to the server.
- Test your issue.
- If your issue is still happening, open a GitHub issue or send a message in the Discord server.

---

## Sponsors
![YourKit Logo](https://www.yourkit.com/images/yklogo.png)

YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications.

YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/dotnet-profiler/), and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

---

## 📦 Download 📦

There are three sources for the plugin:
- [Modrinth](https://modrinth.com/plugin/evenmorefish)
- [GitHub Releases](https://github.com/EvenMoreFish/EvenMoreFish/releases)
- [Jenkins](https://ci.codemc.io/job/EvenMoreFish/job/EvenMoreFish/) (Experimental)

---

## ⭐ Features ⭐

* ### Custom item support

Use any in-game item, or even a base-64 head in place of a fish and with the option to give the player effects, it makes
this plugin highly customizable for use in your own server.

* ### Rarities

There's 4 rarities in the default config, but it's possible to add your own in the `rarities` folder, with its own
colour, drop frequency, standard fish length. It's also possible to set per-fish lengths.

* ### Baits

Boost the chances players have of catching certain fish or rarities with "baits", you can choose these to be disabled
during competitions to give equal fairness to new players and existing players. With the /emf admin bait command you can
give baits to players using the **-p:** variable.

* ### Competitions

Competitions are scheduled, or staff-triggered events where players compete to get the biggest, fattest fish. It's
recommended to change the rewards to your liking, and multiple reward types can be given (Command, Message, Money,
Effect, Item, [and more](https://evenmorefish.github.io/EvenMoreFish/docs/configuration/reward-types)). A customizable bossbar also exists, and kindly vanishes once the competition is up. The permission node
for being able to start competitions via /emf admin competition start is: `emf.admin` (defaulting to op)

* ### Shop

Each fish rarity has its own shop multiplier value. This is multiplied by the fish's length to get the amount of money
the fish will sell for in the clean /emf shop, providing another source of income for your players. Items placed into
the shop are protected, and will drop to the player's feet in the event of the inventory being closed, or a server
shutdown.

* ### Configuration

This plugin is *highly* customisable, pretty much everything can be modified to your liking, including but not limited
to the messages (`messages.yml`). There is a detailed explanation for each line in the config to guide you through the
process.

--- 

## ⚙ Contributing ⚙

Contributions of any size will be massively appreciated, and help support the plugin.

### Translating

To contribute to translations, please visit our [Crowdin page](https://crowdin.com/project/evenmorefish) and suggest changes over there.

If Crowdin doesn't have the language you want, feel free to request it, and we will add it as soon as we can.

Cheers, Oheers


---

## 🎣 Simulator V2 — Phase 04–05 (2026-10-11)

Phiên bản source này bao gồm Phase 01–03 và **Phase 04–05 admin-only ambient packet fish preview**.

- Virtual fish shared/private theo `FishAgent`, không spawn mob thật và không phát reward.
- `WaterIndexService` quét chunk đã load theo giới hạn; không force load.
- `PacketVisibilityService` lọc zone/world/distance/quyền viewer, hỗ trợ LOD và ngân sách packet.
- Adapter PacketEvents 2.14.0 chỉ có trong `even-more-fish-plugin-1-21`, `compileOnly`, có `softdepend`.
- Cấu hình: `simulator.yml > simulator.ambient-preview` và `simulator/visuals.yml`.
- Permission: `emf.admin.simulator.preview`. Chỉ OP/admin có quyền được xem preview cá.
- Bật ở server test: đặt `simulator.enabled: true`, `ambient-preview.enabled: true`, bật ít nhất một zone hợp lệ, chạy `/emf admin simulator reload` và `/emf admin simulator status`.
- **Mặc định OFF**; toàn bộ player vẫn sử dụng pipeline EvenMoreFish vanilla.
- **Chưa thực hiện** Simulator bite/catch/reward, custom ItemDisplay và full Gradle/Paper runtime validation.
- Không claim Folia support.

Test source không cần Gradle:

```bash
./qa/run-parse-only.sh
./qa/run-offline-smoke.sh
./qa/run-simulator-phase23-smoke.sh
./qa/run-simulator-phase45-smoke.sh
```

Báo cáo chi tiết: [`PHASE_04_05_IMPLEMENTATION_REPORT.md`](PHASE_04_05_IMPLEMENTATION_REPORT.md).  
Danh sách test Paper cần làm: [`qa/PHASE_05_PAPER_TEST_MATRIX.md`](qa/PHASE_05_PAPER_TEST_MATRIX.md).


---

## Phase 06 — Admin Fish AI preview (2026-10-11)

The new `ai/FishAiDirector` uses actual Phase 04–05 packet fish and the Phase 03 admin native-hook probe to demonstrate **attraction → indexed-water approach → bite → reel**. The result `SUCCESS_PREVIEW_ONLY` **never grants an item, EXP, bait charge, competition point or stat**. Normal players keep legacy EMF. The new `simulator.ai-preview.enabled` flag is **false by default** and requires both `ambient-preview` and `native-hook-probe`, a valid Simulator zone, PacketEvents, and both admin permissions.

Reference: `PHASE_06_IMPLEMENTATION_REPORT.md`, `qa/PHASE_06_PAPER_TEST_MATRIX.md`.

```bash
./qa/run-simulator-phase6-smoke.sh
./qa/run-simulator-phase45-smoke.sh
./qa/run-simulator-phase23-smoke.sh
./qa/run-offline-smoke.sh
./qa/run-parse-only.sh
```

**Do not deploy this build as production Simulator.** Gradle dependency resolution and live Paper G3/G5/G6 tests remain pending; reward integration is Phase 07.

## 🎣 Simulator V2 — Phase 07 (admin reward integration only)

Phase 07 builds on Phase 06 with a FishKey-locked `EMFFishCaughtEvent` → item delivery → bait/rewards/competition/history pipeline. An internal event-identity gate defers EMF's DB stats until **after delivery**. Duplicate reel, cancelled event, late reload, reservation mismatch and failed delivery are terminal/rejected as appropriate.

**IMPORTANT:** `simulator.reward-bridge.enabled: false` and `paper-gates-verified: false` by default. Both must only be enabled by an admin on a nonproduction Paper test server **after verifying G3/G5**. In this phase, only users with both `emf.admin.simulator.probe` and `emf.admin.simulator.preview` permissions can participate. Normal players still use legacy EMF.

Unsupported native treasure and packet bait-loot categories **fall back to legacy EMF** for safety (`fishing.only-fish: true`, `bait.catch-percentage > 0`, or enabled native provider loot). See `PHASE_07_IMPLEMENTATION_REPORT.md` and `qa/PHASE_07_PAPER_TEST_MATRIX.md`. Full Gradle compilation and runtime Paper verification remain required.

## Phase 08 — Visual Preview / HUD / Catch Cinematic (staging only)

Phase 08 adds bounded packet-only catch cinematic, localized bite actionbar countdown, configurable rarity FX in `simulator/effects.yml`, and `/emf admin simulator effects on|off`. **Both `effects.enabled` and `cinematic.enabled` are false by default.** Simulator/reward remain admin-only behind native Paper gates; this source is not a production release.

- Admin setup/troubleshooting: `docs/simulator/PHASE_08_ADMIN_GUIDE.md`
- Phase report: `PHASE_08_IMPLEMENTATION_REPORT.md`
- Runtime checklist: `qa/PHASE_08_PAPER_TEST_MATRIX.md`
- Isolated smoke: `qa/run-simulator-phase8-smoke.sh`

The sandbox still cannot fetch Gradle distribution, so full Gradle compile and actual Paper/PacketEvents checks are **not verified**.

## 🛡️ Phase 09 — Performance / Compatibility Hardening (2026-10-11)

`PacketVisibilityService` now reconciles stale fish **before** spawning/moving visible fish, shares cleanup priority across viewers, uses a bounded nearest-N selection heap and preserves failed destroy ownership for retry. `SimulatorModule` trips emergency mode on any unhandled preview tick failure, including region/packet providers; AI begin failure no longer leaves a suspended hook. Admin `/emf admin simulator doctor` and `status` expose packet failures/destroy backlog and tick samples. JUnit + isolated stress tests simulate 1/10/30/50/100 clients, but this is **not an in-game MSPT measurement**.

Production gates G3/G5/G7/G9 remain blocked; see `PHASE_09_IMPLEMENTATION_REPORT.md`. No default flags were enabled and no player-facing public Simulator route was introduced.

## Simulator Premium Phase 13 — Environment & Fish Personality (staging)

Source now includes `simulator/ecosystem.yml` (OFF by default), `EnvironmentSampler`,
`FishPersonalityService`, and viewer-only `SurfaceFxService` for light ripples,
bubbles, lure pulse and bite disturbance. No new fish rewards, catch odds or real
Bukkit fish mobs are created. Use `/emf admin simulator status` for sample/FX metrics.

Details: `PHASE_13_IMPLEMENTATION_REPORT.md` and
`docs/simulator/PHASE_13_ECOSYSTEM_ADMIN_GUIDE.md`.
Manual Paper acceptance: `qa/PHASE_13_PAPER_TEST_MATRIX.md`.

**This is still staging source**: full Gradle compilation and Paper G3/G5/G7 have
not been verified; do not enable Simulator rewards on production.

## Premium Phase 14 — Rare Encounter Director (staging)

New `simulator/encounters.yml` provides rare-presence/GOLDEN/SHADOW/GHOST/RARE_SCHOOL visual
annotations for **existing fish agents**. Default OFF. No bonus fish, reward or rarity changes.
`/emf admin simulator encounter preview <kind>` selects an existing, rendered eligible fish.
See `PHASE_14_IMPLEMENTATION_REPORT.md` and `docs/simulator/PHASE_14_RARE_ENCOUNTER_ADMIN_GUIDE.md`.
Run `bash qa/run-premium-phase14-validation.sh` for headless checks only; Gradle/Paper gates
are still blockers before production rollout.

## 🐠 Premium Phase 15 — Per-viewer Custom Packet Fish Models (STAGING ONLY)

This phase adds `simulator/models.yml` (OFF by default), `FishRenderPolicy`, `FishPackAudience`, and the Paper-1.21-only PacketEvents `ItemDisplay` renderer. An existing `FishAgent` can be displayed as a `minecraft:item_model` item to a viewer who has **acknowledged the exact configured resource pack UUID**, while other viewers see the original vanilla fish. Privacy, agent count, packet budgets, reward IDs and catch logic are unchanged.

Model changes are applied by *destroy → respawn* per viewer and respect the existing packet cleanup retry ledger. The Premium Cinematic reuses the same model policy. This phase does **not** provide finished custom 3D textures/models or a pack-delivery server; `docs/simulator/resource-pack-example/` is only an editable minimal pack structure with a vanilla cod placeholder.

- Implementation: `PHASE_15_IMPLEMENTATION_REPORT.md`
- Configuration/setup: `docs/simulator/PHASE_15_CUSTOM_MODELS_ADMIN_GUIDE.md`
- Manual acceptance: `qa/PHASE_15_PAPER_TEST_MATRIX.md`
- Headless regression (not a Paper runtime substitute): `bash qa/run-premium-phase15-validation.sh`

**No production support is claimed:** Full Gradle build cannot download Gradle 9.5.1 from `services.gradle.org` in this environment. Native G3, renderer G5, reward G7 and custom pack/model G15 all require live Paper testing.

### Simulator Premium Phase 16 — staging hardening, not a release

The Phase 16 changes keep unresolved virtual entity IDs across retry ledger
capacity overflow, and rotate failed destroy attempts fairly so a broken viewer
cannot starve others. Run `bash qa/run-phase16-validation.sh` for headless tests.
The optional `.github/workflows/phase16-staging-build.yml` demonstrates a real
Gradle/JUnit/shadowJar build on a connected CI runner.

Read `docs/simulator/PHASE_16_PRODUCTION_VERIFICATION_RUNBOOK.md` and
`qa/PHASE_16_PAPER_TEST_MATRIX.md` before any staging. The evidence checker
`python3 qa/phase16_release_gate.py` remains **BLOCKED** until real logs are
reviewed. No general-player Simulator release has been authorized.
