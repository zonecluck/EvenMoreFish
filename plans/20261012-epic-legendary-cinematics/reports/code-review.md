# Independent cinematic code review

Date: 2026-10-12. Verdict: approved by source review; no actionable production-code defects found in the reviewed change.

## Reviewed behavior

- The old five-argument timeline constructor delegates to STANDARD. Its surface/reveal/delivery equations retain their previous values; Frame and Profile constructor contracts remain unchanged. Native STANDARD duration, arc/orbit, scale, tilt and splash conditions remain the same. Epic/Legendary changes are explicitly selected.
- Packet style comes from the resolved preset object's identity rather than synthetic admin FishKey or arc size. Loader-created profiles remain distinct objects; value-identical profiles do not accidentally become Legendary. This also handles custom FishKeys mapped to a premium preset.
- New StageFxSink.frame is default/no-op for existing sinks. Packet frames run before the hidden-stage continue, so anticipation/vortex patterns are reachable. Runtime/linkage failure remains isolated and counted; it does not reach rewards.
- A single static ledger uses Bukkit.getCurrentTick for both renderers. Colored frame emissions, native STANDARD bubbles and packet stage/trail emissions reserve before sending. Individual particles count toward the shared 24-particle ceiling; failed sends retain their reservation. Native frame and packet configured ceilings also remain enforced.
- Dust resolves DUST then REDSTONE and supplies DustOptions. Sparkle/bubble aliases preserve version compatibility. Config's supported-particle whitelist was not relaxed. Every new effect goes through Player APIs rather than World broadcast APIs.
- Native display lifecycle, hidden-by-default visibility, cloned one-item stack and removal paths are retained. Packet spawn-failure/orphan cleanup handling is unchanged. Neither renderer changes inventory, selection, hook, reward or permission operations.
- Live effects/profile cancellation is verified in SimulatorModule.setOwnEffects and setOwnHudProfile: disabling effects or selecting a non-cinematic profile immediately stops both native and packet scenes. The new frame sink additionally checks online/effects/particle preferences. A redundant cinematic check inside that sink is not needed for the current call path.
- Added tests cover enhanced stage joins and pose, vertical targets, shared reservation arithmetic, bounded particle patterns and resolved style identity. Isolated smoke input now compiles the real helper against API scaffolding; these stubs do not prove actual particle transport or client rendering.

## Completion notes

- At review time the operator guide still described Epic/Legendary scenes as 1.6/2 seconds. The implemented native durations are 62/80 ticks (3.1/4 seconds), so update that statement before delivery.
- The configured packet FX ceiling intentionally changes from 8 to 12. Common/Rare preset motion/cue values are unchanged apart from supplying previously required numeric defaults, but their concurrent emissions can be less clipped under the larger overall ceiling. Do not describe the overall particle limit as unchanged.
- Motion tests demonstrate mathematical continuity; they do not establish ItemDisplay interpolation quality or real two-client privacy. Paper/client startup, rendering, cancellation, cleanup and profiling remain unverified by this source review.
- Full Gradle/package verification is running independently. This reviewer did not run builds or weaken checks.

Status: DONE_WITH_CONCERNS
Summary: Reviewed implementation is suitable for the requested ordinary-catch and admin-preview polish; no source defect requiring changes was identified.
Concerns/Blockers: Update duration documentation and retain accurate runtime-evidence boundaries. Build/package results must be supplied by the independent verifier.
