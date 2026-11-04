# Fishing Pool implementation report

Date: 2026-10-12. Status: implemented; controller owns integrated Gradle and Paper/client verification.

Implemented persistent pool model/settings/store/service and shared command operations with adapters for 1.20, 1.21 and 26.1. Admin registration changes add only the pool branch. Configuration defaults to enabled with zero pools; Simulator flags are untouched.

Public integration API: `FishingPoolService(EvenMoreFish)`, `reload(): boolean`, `shutdown()`, `track(FishHook)`, `untrack(UUID)`, `quit(UUID)`, `multiplier(Location,String)`. Controller must track only accepted ordinary casts after rod/buff timer changes, stop tracking on terminal states and quit, reload/shutdown through plugin lifecycle, and restrict rarity multipliers to native fishing contexts. `FishingPool.applyWeight` keeps zero/nonfinite weights ineligible and finite multiplied output.

Geometry uses exact world names, XZ radius and configurable vertical tolerance. Overlaps select nearest center then normalized ID, without stacking. Native positive countdown is shortened once after BOBBING/water landing; enchantment Lure is already applied to that countdown. Leaving restores removed ticks while preserving elapsed time, initial rod/buff min/max and applyLure. Re-entry does not compound the bonus. End of the wait stage restores Lure without reviving an approaching fish.

One synchronous task checks tracked hooks every two ticks, removes invalid/offline/wrong-world/stale hooks, and emits rings every 20 ticks. Hard limits: 256 pools, 2048 tracked hooks, 768 particle sends per ring update, 8–64 ring points; ring start rotates to avoid permanent starvation. World/chunk checks avoid loading chunks. No entities or reward callbacks are created by pools.

YAML loads validate the entire candidate before touching existing state. Save validates first and atomically replaces the same-directory YAML, refusing nonatomic fallback; active document updates only after successful write. IDs normalize case but removal uses the original YAML key, so manually configured `Lake` is removed by `lake` correctly. Failure deletes the temporary file and leaves the old target intact.

Verification: manual `javac --release 21` against actual cached Paper 1.20.1, SnakeYAML 2.2 and JUnit Jupiter/Platform ran all 13 focused tests successfully, zero skipped. Tests cover geometry, overlap, exact rarity IDs/Common exclusion, strict types/nonfinite rejection/clamping, YAML round trip, one-time countdown restoration, actual WeightedRandom distribution and no zero-weight revival. The two real-file atomic persistence tests cover uppercase-key restart/removal and failed replacement cleanup. Controller integrated Gradle rerun required after final persistence edits. No mocks or synthetic server claims.

Reviewed integration concerns with controller: fish hunting receives no pool bonus; MONITOR tracking excludes late cancellation; command permission declaration belongs to controller variant build configs. Fixed trailing blank EOF in the touched legacy admin command. No commits or deployments performed. Real Paper/client speed, radius visibility, animation and lifecycle acceptance remain unverified.
