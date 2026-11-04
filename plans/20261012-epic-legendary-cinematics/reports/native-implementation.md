# Ordinary catch cinematic implementation

Date: 2026-10-12.

Touched implementation: `even-more-fish-plugin/src/main/java/com/oheers/fish/effects/VanillaCatchCinematicService.java`.

Epic scenes use 62 ticks (6/8/14/14/14/6), arc height 1.6 and orbit 0.55. Legendary/Mythical scenes use 80 ticks (8/12/16/18/18/8), arc height 2.1 and orbit 0.8. Both select the shared explicit style and apply its scale, yaw and roll to the existing private ItemDisplay. STANDARD retains its former durations, motion, scale, tilt, splash timing and bubble cadence.

A scene-owned set records first stage entry. Higher styles play a modest splash at surface break, Epic amethyst chime or Legendary level-up at reveal, and quiet pickup during delivery. Live sound preferences gate every cue; a muted cue is not replayed later. The shared emitter supplies private colored particle geometry during hidden anticipation/vortex and the visible stages, gated by live particle preferences. The native frame consumes at most 24 particles across all scenes, and both richer emissions and STANDARD bubbles reserve against the shared actual-server-tick cap before sends. Returned particle failures retain their reservation and are rate-limited in the existing warning path.

Eligibility, cooldown/concurrency limits, cloned item, viewer privacy, cancellation and cleanup are unchanged. No camera lock, title, rewards, configuration keys or independent task was added.

Validation: `git diff --check -- even-more-fish-plugin/src/main/java/com/oheers/fish/effects/VanillaCatchCinematicService.java` passed. Shared helper signatures and pose units were checked against the controller's source. No build was run by this worker while baseline testing was active; controller integration builds and actual Paper/client interpolation, particle privacy and cleanup remain necessary.
