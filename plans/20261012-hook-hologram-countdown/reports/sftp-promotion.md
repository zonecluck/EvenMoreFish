# Hook hologram promotion

Date: 2026-10-12, Asia/Saigon. Implementation and selective upload complete; restart/client proof pending.

`/plugins/even-more-fish-2.3.6-SNAPSHOT-1.21.jar` was backed up and replaced through the user's existing SFTP authorization. Final SHA-256 after fresh remote readback: `0f21fb8eeb4140afd9467114c701e731db13ee1655ffd58f9a59965d40337271`.

Remote rollback folder: `/.emf-hook-hologram-20261012-233244/`, containing verified before/after/displaced JARs and manifest. Restore `before/plugins/even-more-fish-2.3.6-SNAPSHOT-1.21.jar` to the live path after stopping the server, then restart.

The fresh installed baseline matched the previous cinematic JAR (`c9db5e1500fd4f80f3b87909c1697b4ab2266346f09d26c29b2f8fbd95eae64d`). Only three existing FishingHookHolograms entries changed and six countdown/native-timer entries were added; no entries removed, no other changed entries. All cinematic/resource bytes are identical. Runtime `simulator.yml`, `simulator/effects.yml`, and `simulator/cinematics.yml` remain hash-identical; no YAML, main config/messages, database or rewards were modified.

The hook owner no longer sends bite/missed titles or clears other feature titles. Its two-line display uses an aqua waiting header or cached animated reel prompt, plus twenty cyan/dark bar segments and white native tick-seconds at two-tick refresh. Known zero bite window reverts to actual waiting state after a canceled/expired bite; unsupported counters omit the numeric line. All native access is read-only and exact named fields/API, with no guessed older mappings.

Evidence: [21 focused/131 full tests and all-variant package](final-verification.md), [review](code-review.md), [timer API sources](timer-api-investigation.md), [entry comparison](jar-entry-diff.json), [durable promotion manifest](sftp-deployment.json), [worktree audit](worktree-audit.md).

Restart the server to load these classes; config reload cannot replace restart. In real Paper/client validation, cast a rod and observe waiting/approach countdown, bite prompt/window, late cancellation, missed timeout, reel/catch/quit/world-change cleanup and idle updater behavior. Confirm the second line does not wrap under the actual resource pack. Weather and TPS can affect elapsed wall-clock time; displayed seconds reflect native counters. No live client/native reflection or MSPT claim, commit/push or console restart was made.
