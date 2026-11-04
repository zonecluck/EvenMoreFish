# Cinematic plan review

Date: 2026-10-12. Verdict: executable with the corrections below. Review is source-based; no builds or Paper/client checks were run by this reviewer.

The plan keeps decoration separate from rewards, retains the legacy constructor and Frame contract, and covers both ordinary ItemDisplay catches and admin packet preview. The existing cleanup owners should remain in place.

## Required implementation details

1. **Resolve style from the selected preset.** Admin preview uses `Preview:VisualOnly`, so rarity-based style lookup will silently leave Epic/Legendary previews STANDARD. Associate style with resolved profiles, retain the old Profile constructor if adding a component, and default the old timeline constructor to STANDARD. A custom rarity mapped to Epic should receive Epic decoration without changing eligibility or rewards.
2. **Define the budget owner and unit.** The packet service currently resets its FX budget with the Simulator tick; native scenes run a separate two-tick timer. Reusing geometry does not share those budgets. Cap all concurrent scenes in each renderer, and use a single Paper tick source if the claim is a combined cap across both renderers. Count individual particles, not only calls carrying an arbitrary count. Reserve before emission, including calls that throw. Do not let a new ring bypass the existing cue/trail ceiling.
3. **Send hidden-stage frame FX.** The frame callback must run before the existing `if (!frame.visible()) continue` so anticipation/vortex effects actually animate. Keep the callback default/no-op for old StageFxSink implementations. Catch callback runtime/linkage failures exactly as existing stage and trail failures are isolated.
4. **Honor live viewer preferences.** Check effects and particle preferences for every new emission, not only start. Native scenes already stop when cinematic/effects is disabled; packet frame FX should check cinematic preference as well as effects/particles before emitting. Sounds still obey sound preference. Never call world-level particle/sound APIs.
5. **Handle dust aliases and typed data.** Core compiles against Paper 1.20.1, while the destination uses 1.21.11. Resolve `DUST`/`REDSTONE` dynamically and send DustOptions; an untyped dust call can throw and stop native scenes. If neither alias is available, skip cosmetic dust while keeping movement and cleanup operational. Do not weaken the existing config particle whitelist merely to support internal colored FX.

## Verification and scope notes

- Test every stage join for Epic and Legendary position continuity and endpoint velocity, including zero-reveal durations and vertical/zero-horizontal water-to-target vectors. Keep STANDARD regression cases unchanged. New tilt/rotation and scale envelopes should end where the next stage starts; position-only tests cannot demonstrate ItemDisplay pose continuity.
- Geometry tests should check finite coordinates, bounded radius/count and distinct Epic/Legendary output. Verify preferred style routing for admin preview explicitly rather than inferring success from a synthetic rarity.
- Preserve ordinary catch MONITOR gating, cloned one-item visual, invisible-by-default display and existing stop/quit/reload/disable handling. Retain packet orphan cleanup queues and spawn-failure recording. Adding cosmetic callbacks does not justify a new entity owner.
- Keep durations within the validated 8..90 ticks. A longer ordinary reveal requires updating the operator guide's current 1.6/2 second statements.
- Default YAML repairs are justified by the existing loader contract; preserve config versions and common/rare presets. Remote edits must merge only the intended preset keys into a fresh snapshot while retaining enabled admin-preview settings and reward/public-mode flags.
- Do not claim Paper interpolation, client privacy, runtime cleanup or MSPT from geometry/unit tests or SFTP hashes. Actual two-client observation after clean restart remains required for those claims.

Status: DONE_WITH_CONCERNS
Summary: Approved to implement once the explicit preset routing, emission budget ownership and typed viewer-private particle details are incorporated. Existing lifecycle and reward boundaries remain suitable.
Concerns/Blockers: Runtime rendering and cleanup proof remain pending; no production Simulator/reward gate approval is implied.
