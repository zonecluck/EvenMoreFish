---
date: 2026-10-12
---
# Hook hologram native countdown

Context: ordinary fishing feedback belongs beside the hook. Removed repeated BITE/MISSED screen titles and hologram cleanup's global `clearTitle`; ordinary catch and competition titles remain intact.

What happened: `NativeHookTimers` reads actual wait, approach and nibble counters without timer writes. Access uses public getters and exact Mojang fields; unknown legacy mappings omit numbers rather than guessing.
BITE sampling waits for a scheduled update because Minecraft initializes nibble after the event. A known zero reconciles to WAITING and reads the native wait/approach phase, including late event cancellation; unknown data remains unknown.
`HookCountdownLine` adds twenty colored segments and nominal native-tick seconds at 10Hz. This is an active-phase counter, not a guaranteed wall-clock ETA or a fabricated total through randomized phases.

Reflection: per-class accessor results, 120 animation frames and the last rendered component are cached; per-hook normalization tracks observed phase maxima. Invalid hooks, offline owners and world changes remove displays; an empty collection stops the updater.
Decision: preserve read-only fishing, pool/Lure behavior, reward contracts and prior Epic/Legendary cinematics. Holograms retain their existing world-visible audience; no private-viewer contract was added.

Evidence: the regression first produced four expected title-control failures. Final focused 21 tests, full 131 tests, all three variant packages and all ten Phase9 suites passed; [verification](../../plans/20261012-hook-hologram-countdown/reports/final-verification.md).
The [timer investigation](../../plans/20261012-hook-hologram-countdown/reports/timer-api-investigation.md) and [source review](../../plans/20261012-hook-hologram-countdown/reports/code-review.md) establish timing semantics and corrected cancellation behavior.

Next: restart and real Paper/client checks remain pending for runtime reflection, line wrapping, weather/lag behavior and cleanup. Upload is not confirmed. AgentWiki publishing was skipped because external sharing is outside the requested task.
