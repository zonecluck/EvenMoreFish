# EvenMoreFish Simulator V2 — Phase 09 handoff

Cumulative source Phase 01–09; see `MASTER_IMPLEMENTATION_PLAN_EvenMoreFish_Simulator.md`, `PHASE_09_IMPLEMENTATION_REPORT.md`, `docs/simulator/PHASE_09_ADMIN_OPERATIONS.md` and `qa/PHASE_09_PAPER_TEST_MATRIX.md`.

**STAGING SOURCE ONLY — NOT PRODUCTION.** Do not enable `simulator.reward-bridge.enabled` or claim `paper-gates-verified` based on static tests. All visual / AI / reward flags must remain OFF by default. Do not route ordinary players through Simulator until the public-mode implementation and G3/G5/G7 runtime gates are fully validated.

Never treat synthetic packet transport benchmarks as actual Paper MSPT; run a real Paper client integration test. Maintain legacy EMF fish item/event/rarity/bait/rod/competition behavior, existing `config.yml` migration, and source compatibility with other variants. Follow single-owner Paper thread rules, do not claim Folia. PacketEvents is optional external dependency in Paper 1.21 adapter, not shaded.

When continuing in Codex, first obtain a full Gradle build and reproduce any type errors. Run `bash qa/run-phase9-regression.sh` and JUnit, then stage G3/G5/G7. Be aware that native treasure and bait-loot lack packet equivalent and are intentionally legacy fallback. Item delivery partial failure remains a release blocker.


## Phase 9.1 continuation

New `PacketCleanupQueue` is a required javac input for the isolated Phase 4–5, 8, 9 and 9.1 smoke scripts. `AmbientPreviewService` deliberately retains unresolved cleanup across reload and must not silently discard orphan IDs. `SingleSlotDeliveryPlanner` performs whole-stack one-slot planning only; keep tests and do not revert to addItem+leftover partial drops. Both changes are STAGING-only; actual Paper G3/G5/G7 and Gradle build remain mandatory.

## Premium Phase 10 (staging-only)

Presentation uses a single owner via `presentation/FishingPresentationService` and `PresentationArbiter`; do not send independent Simulator ActionBars in listeners or effects. The file `simulator/presentation.yml` is OFF by default, as is the master Simulator flag; admin may enable ONLY in staging. Player `/emf hud` updates UUID preferences async. Do not parse MiniMessage from player input. No GUI has been added yet; any GUI work must be session/holder safe. Current cinematic remains the Phase 8 arc, not the proposed multi-stage Phase 11 director.

Test with `qa/run-phase9-regression.sh`, `qa/run-premium-phase10-smoke.sh`, `qa/run-premium-service-smoke.sh`, then Gradle/Paper when dependencies are available. Gradle is still a release blocker, and G3/G5/G7 remain unverified. This document does not grant release approval.

## Premium Phase 14 continuation (staging-only)

`simulator/encounters.yml` gates purely cosmetic rare presence; OFF by default. Every encounter
references existing FishAgent UUIDs; never add fish, change FishKey, change rarity probability,
call EMF rewards, or bypass Fish AI reservations. Keep per-viewer `PacketVisibilityService.shownTo`
checks and privacy scope, bounded encounter FX and strict loader validation.

Exact FishKey override is a COSMETIC type mapping, not an eligibility/reward override.
Staff preview must choose a real eligible, already-rendered fish. GOLDEN particles resolve
old REDSTONE/new DUST enum names because EMF core still compiles against Paper 1.20.1.

Run `bash qa/run-premium-phase14-validation.sh`, then full Gradle on a networked build host,
then actual Paper G3/G5/G7 acceptance. Do NOT certify production from Java smoke tests.

## Premium Phase 16 continuation — release blocked

Phase 16 fixes `PacketCleanupQueue` head-of-line starvation with bounded FIFO
rotation and `PacketVisibilityService.clearBestEffort()` ledger overflow tracking.
Do not revert these without reproducing `bash qa/run-phase16-smoke.sh` (includes
>4096 in-flight virtual IDs). `qa/run-phase16-validation.sh` exercises existing
headless suites. The JUnit tests and CI workflow are supplied but NOT yet
run by Gradle in this environment.

The strict release manifest is `qa/evidence/PHASE16_RELEASE_EVIDENCE.json`.
`python3 qa/phase16_release_gate.py` intentionally returns exit code 2 while
Gradle or Paper/client evidence is missing. It is NOT permission to toggle
production modes if someone simply marks a JSON field PASS. Follow the Phase16
runbook and capture real Paper G3/G5/G7/G15 + profiler evidence, then obtain
human sign-off. Public Simulator mode is still not implemented.
