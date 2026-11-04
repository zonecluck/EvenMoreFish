# Hook countdown independent code review

Date: 2026-10-12. Final verdict: approved by source review after both initial findings were corrected. No Gradle or client tests were run by this reviewer.

## Resolved findings

1. **Canceled BITE reconciliation now follows the real native window.** The delayed update reads exact nibble; a known zero switches the hologram to WAITING and samples the actual wait/approach counter. The investigated native ordering initializes positive nibble after a successful BITE event and before the next scheduled update, so this does not prematurely clear a successful bite. It also handles cancellation after this plugin's MONITOR handler. Unknown mappings remain unknown rather than being interpreted as zero. MISSED retains its separate state and skips countdown updates. The regression supplies exact zero nibble plus wait=100 and asserts the waiting heading with 5.0s and no title/timer writes.
2. **Animation cadence test corrected.** `FishingHookHologramsTest.biteAnimationUpdatesHookDisplayWithoutSendingTitles` now invokes two scheduled ticks before asserting display animation. Title-negative and float-coordinate checks remain.

The display width was increased to 240 to accommodate the twenty segments and seconds on one second line. Actual font/layout behavior remains a client validation point.

## Verified boundaries

- All hook hologram showTitle, sendTitlePart and clearTitle calls are gone; ordinary catch/competition title behavior is untouched by the reviewed files.
- Wait reads the public live counter. Approach uses a cached optional public getter or exact Mojang field; bite uses exact nibble. No setters, configuration bounds, local elapsed countdown or guessed obfuscated mappings are used.
- ClassValue caches positive and negative accessor resolution per hook class. Native BITE is sampled from the scheduled update rather than inside the event, respecting post-event nibble initialization.
- Unknown/inaccessible counters omit numeric text. The second line uses twenty bounded colored segments and native ticks converted to nominal tenths of a second; normalization tracks observed phase maxima and reflects later native pool reductions/restorations.
- Existing float anchor geometry and TextDisplay properties are preserved. Text mutation is limited to every two ticks and unchanged components are skipped. Owner quit/world mismatch now removes the display; empty updater cancellation remains intact.
- Existing holograms are world-visible TextDisplays, as before. This change does not add a private audience contract; unlike the catch cinematics, holograms were not viewer-private in the baseline.

## Evidence limits

The timer investigation establishes nominal native-counter semantics, not guaranteed wall-clock ETA. Legacy 1.20.1 obfuscated mappings remain deliberately unsupported; unknown approach/bite counters keep the heading only. Real 1.21.11 reflection access, screen layout, weather/lag behavior and cancellation rendering need Paper/client validation after restart.

Status: DONE_WITH_CONCERNS
Summary: Read-only countdown, title removal and lifecycle boundaries are sound; both initial source/test findings are resolved and no additional source defect was identified.
Concerns/Blockers: Independent tester owns build/package results. Real client layout and runtime mapping/countdown behavior remain unverified by source review.
