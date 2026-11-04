---
title: Vanilla rare catch cinematics and Fishing Pools
status: in-progress
priority: P1
effort: medium
branch: master
tags: [fishing, cinematics, pools]
created: 2026-10-12
---

# Vanilla rare catch cinematics and Fishing Pools
Updated: 2026-10-12. Implementation complete; local verification passed with full-suite and runtime gaps. Scope: ordinary EMF fishing, premium timeline motion, persistent admin pools; existing Simulator production/reward flags remain unchanged. No deployment or commit requested.

## Source and decisions
Reference: C:/Users/hp/IdeaProjects/MineBox/LeDatFishing/src/main/java/org/ledat/leDatFishing/hotspot (requested root path does not exist).
- Pool geometry: source uses horizontal radius only; EMF also bounds vertical distance to avoid boosting another floor.
- Speed: source tracks hotspot near hook; EMF waits for actual water landing, then shortens native wait once; no duplicate scheduler per cast.
- Rarity: multiply eligible rarity weights by exact configured IDs, preserving zero inputs and requirements, rod/bait/region/competition checks. Existing selector all-zero fallback remains unchanged.
- Effects: bounded nearby-viewer rings without forcing chunks or persistent entities.
- Cinematics: ordinary fishing uses a private nonpersistent ItemDisplay with transform interpolation, short scenes, cooldown, no camera/input changes or reward callbacks. Simulator packet scenes remain staging-only.
- Common exclusion: exact case-insensitive exclusion always wins over rarity heuristic or explicit rare IDs.
- Overlapping pools: nearest center then ID wins; bonuses do not stack.

## Phases
1. [Implementation and verification](phase-01-implementation.md): source implementation complete; focused JUnit and headless smoke passed; review finding fixed. All-variant package build and JAR content verification passed. Full core suite has two existing harness failures; Paper/client acceptance and release evidence remain pending.

Dependencies: cached Paper/CommandAPI dependencies and Corretto 21 for local builds; a real Paper server and connected client for behavior acceptance. Evidence: [pools](reports/pools.md), [smoke tests](reports/smoke-tests.md), [source review](reports/code-review.md), [final verification](reports/final-verification.md), [admin guide](../../docs/fishing-pools-and-cinematics.md).

## Acceptance
- [x] Source and local policy tests exclude Common from cinematics; ordinary delivery/replacement precedes cosmetic processing without enabling Simulator.
- [x] Source includes new-cast, quit/world, reload/disable cleanup, cooldown and global concurrency limits.
- [x] Admin pool commands and persistent storage implemented for 1.20, 1.21 and 26.1; radius, speed and rarity multiplier configurable.
- [x] Water/native wait bookkeeping, geometry and eligible rarity multipliers covered locally; canceled casts excluded at MONITOR.
- [x] Ring effects bounded, nearby only and use loaded chunks.
- [x] Existing dirty edits, reward/event/rod/bait contracts and staging flags preserved at source level.
- [x] Focused Gradle tests, full core suite, headless smoke and source review attempted; limitations recorded.
- [x] All-variant compile/package passed with explicit `-x test`; separate focused/full test results recorded.
- [ ] Paper/client verifies native speed with Lure/custom rod/buff, radius rendering, private smooth scenes and full lifecycle cleanup.
- [ ] Release evidence manifest and required human signoff complete before any Simulator production release claim.

Phase checklist: 11/13 complete; remaining items are runtime acceptance and release evidence. Plan remains `in-progress` because those gates are pending. Completed implementation is not full production acceptance.

## Risks and rollback
Visual smoothness requires real client verification. Bonus speed interacts with Lure; do not apply Lure twice. Invalid configuration retains last valid pools. Disable `animations.rare-catch.cinematic.enabled` or `fishing-pools.enabled` and reload to roll back without deleting pool definitions; `fishing-pools.particles.enabled` controls only rings and `animations.rare-catch.enabled` controls all rare-catch cosmetics.
