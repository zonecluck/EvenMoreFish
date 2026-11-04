---
title: Implementation and verification
status: in-progress
created: 2026-10-12
---

# Implementation and verification
Updated: 2026-10-12. Source implementation complete; verification has outstanding runtime/release gates.
Read local AGENTS.md, FishingProcessor, FishManager, AnimationManager, EvenMoreFish lifecycle and version command adapters. Preserve all existing dirty edits.
Pool module owns new fishing/pool classes, fishing-pools.yml and pool command adapters. Controller owns lifecycle, FishingProcessor and FishManager integration, cinematic classes/config and timeline changes. No deployment or commit requested.

## Reconciled work
- [x] Implement pool geometry/settings, native wait tracker, bounded rings and atomic persistent storage.
- [x] Register shared create/list/info/remove command contract in 1.20, 1.21 and 26.1; declare pool permission.
- [x] Integrate ordinary cast tracking, native-hook rarity context and lifecycle cleanup without changing Simulator gates.
- [x] Add private ordinary ItemDisplay cinematic, Common/excluded rarity policy, cooldown, bounded concurrency and preferences.
- [x] Improve shared timeline position/scale continuity without reward callbacks or camera control.
- [x] Verify focused geometry, weight, wait, YAML/atomic persistence and fractional timeline tests through Gradle: 21 passed, zero failed/skipped (3 policy, 13 pool, 5 timeline).
- [x] Run full core suite and report limitations: 89 tests, 87 passed, two existing `SingleSlotDeliveryPlannerTest` failures because `ItemStack.isSimilar` reaches null `Bukkit.server` in the harness.
- [x] Run requested headless suites: Phase11, Phase9 and Phase16 passed; standalone rarity policy checks passed.
- [x] Review source and resolve imported mixed-case YAML pool removal with real-file persistence regression tests.
- [x] Update operator guide and README link; preserve historical staging disclaimers.
- [x] All-variant compile/package and addons passed with `gradlew --project-cache-dir qa/gradle-cache build -x test`; all three JAR content checks passed.
- [ ] Execute real Paper/client pool persistence, wait/Lure, ring, scene privacy/motion and cleanup checks.
- [ ] Supply release evidence and human signoff; static Phase16 release gate currently blocks as designed.

## Validation and remaining risks
See [final verification](reports/final-verification.md) and [smoke evidence limits](reports/smoke-tests.md). Source review's mixed-case ID finding is historical: current removal resolves the original YAML key case-insensitively, with focused store tests. Do not claim a clean full core suite or real Paper/client proof.

Rollback uses `fishing-pools.enabled`, `fishing-pools.particles.enabled`, `animations.rare-catch.cinematic.enabled` or the broader `animations.rare-catch.enabled`, then `/emf admin reload`. Keep pool definitions and existing Simulator flags intact. Client smoothness, native hook countdown semantics and real reward/lifecycle behavior still require runtime validation.
