# Source review

Date: 2026-10-12. Scope: new ordinary fishing cinematics, Fishing Pools, timeline continuity, command adapters and lifecycle. Existing dirty Simulator work was not edited. Review used the code-review skill and the implementation plan. Gradle is owned by the controller; no build or live Paper/client test was run here.

## Final actionable status

No unresolved actionable findings remain after the focused source re-review.

The initial **P2 mixed-case YAML removal** is resolved: `FishingPoolService.remove()` now calls `FishingPoolSettings.removeDefinition()`, which locates the original case-insensitive document key and removes that key. `FishingPoolStoreTest.actualAtomicSaveLoadsOnRestartAndPersistsRemoval()` starts with `Lake`, loads runtime `lake`, invokes the same helper, saves an actual temporary-directory file and reloads it to verify removal. The atomic replacement failure regression retains the pre-existing directory marker and checks temporary-file cleanup. Tests were read here; their execution remains controller-owned.

Further final fixes were inspected: pool tracking and pending rare-catch visuals now run in `FishingProcessor.afterFishEvent()` at MONITOR and exclude events canceled by later cancellable handlers; zero horizontal camera direction uses a zero vector instead of normalization; the vanilla emitter checks the particle preference explicitly. The narrow existing build repairs preserve old `FishStats` setter signatures while adding UUID/name snapshot overloads, use `candidate.playerId()` in pending-cast admission, cache modern/legacy particle aliases in `AmbientPreviewService`, and resolve unbreaking through its namespaced enchantment key. No concrete regression was found in these repairs.

## Acceptance review

| Criterion | Source result | Evidence / limits |
|---|---|---|
| Common never causes cinematic | Pass | `RareCatchPolicy` excludes Common before explicit IDs, index and weight heuristics; AnimationManager reuses that policy. |
| Rare ordinary catches display after ordinary delivery without enabling Simulator | Pass | `FishingProcessor.playCatchVisual()` follows give/remove or item replacement; the vanilla service reads ordinary animation configuration and does not enable the Simulator. |
| Reward and cosmetic isolation; canceled EMF catch | Pass | `Processor.commitCatch()` returns null before bait/reward effects when EMF event is canceled; visual service never selects/delivers/rewards and invocation has a cosmetic exception boundary. |
| New cast, quit, world change, reload, disable cleanup | Pass | Accepted new legacy cast stops its previous scene; quit/world hooks, reload/disable clear scenes and restore pool hooks. Invalid display/viewer state and movement also stop scenes. |
| Private nonpersistent ItemDisplay; bounded concurrency/cooldown | Pass | Spawn callback sets `visibleByDefault=false`, persistent=false, then shows only to catcher. Global scene limit and per-player monotonic cooldown apply. Real client privacy/interpolation still require validation. |
| Persistent admin create/list/remove, configurable geometry/speed/rarity | Pass | All three adapter variants register the shared commands and permission; atomic storage and case-insensitive original-key removal are present. Real-file regression covers mixed-case removal. |
| Water/native wait/Lure/leave | Pass at source level | Two-tick tracker waits for BOBBING, loaded water and a positive native countdown; reduces that current countdown once per cast. It restores captured min/max/Lure and removed countdown ticks on departure/reload/shutdown. Live Paper wait-state and Lure proof remains pending. |
| Wrong world, vertical floor, overlap and canceled casts | Pass | Exact world and vertical tolerance plus horizontal radius select nearest center then ID. MONITOR tracks only the accepted snapshot with an uncanceled event, and pending rare visuals are discarded when the event is canceled. |
| Eligible rarity weighting; bait/rod/region/competition contracts | Pass for new multiplier | Both weighted rarity paths multiply the existing weight after existing filters; ordinary marker context gates pool bonuses. Hunt and normal generic RequirementContext/API selection do not gain bonuses. Zero input remains zero through the new multiplication. |
| Bounded nearby ring effects and no chunk loading | Pass | 256 pools, 768 particles per ring tick, bounded ring points, viewer distance/world and loaded-chunk checks; no persistent ring entities. |
| Premium motion continuity | Pass at source level | Shared position endpoints, smoothstep/eased reveal, squared delivery envelope and skipped reveal handling agree with new fractional boundary tests. Actual visual result still needs client proof. |
| Existing staging flags remain off | Pass | Simulator master/reward/verification and presentation/cinematic/model/school/ecosystem/encounter master flags remain false in supplied resources. New ordinary animation flags are independent. |
| Builds and focused tests attempted | Controller-owned | Tests cover geometry, multiplier ratios, wait bookkeeping, YAML/settings/atomic persistence and timeline boundaries. Build results must be supplied by controller. No live acceptance claim is made. |

## Existing behavior noted without changing scope

`WeightedRandom.pick()` retains its pre-existing uniform fallback when *all* effective weights are zero. The new pool multiplier does not turn a zero input positive, and the new test verifies zero exclusion alongside positive alternatives; it does not prove all-zero exclusion for the legacy selector. Do not describe the helper test as proof that every all-zero EMF configuration becomes ineligible. Changing the global fallback would alter an existing shared contract and is not necessary to fix the new pool multiplier.

The vanilla emitter now includes an explicit particle preference check. Before this addition, the current `UiProfile` invariant also excluded particle-disabled profiles through the cinematic entry/tick checks.

Status: DONE
Summary: All requested acceptance criteria were traced through source, and the one concrete persistence defect is resolved with a real-file regression. Build/test execution remains controller-owned; runtime/client certification is outside this source review.
