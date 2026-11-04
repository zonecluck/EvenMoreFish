---
title: Hook hologram countdown
status: completed
priority: P2
effort: small
---
# Hook hologram countdown

Date: 2026-10-12. Implementation, local checks and SFTP promotion complete; restart/client acceptance pending.

Remove the hook hologram's bite/missed title and clearTitle calls. Keep the bite prompt over the actual rod float; add a second line showing a cyan progress bar and remaining seconds, driven by native wait/approach/bite counters. Do not modify fishing timers, rewards, cast policy or other feature titles. Unknown counters must not produce an invented numeric countdown.

Steps: verify native timer APIs and baseline package; implement bounded read-only timer access and two-line hologram; focused regression tests/full core/all-variant package/review; update operator docs; back up/promote only the changed 1.21 JAR through the existing SFTP authorization and verify readback. Runtime client proof remains a restart/operator check.

Touchpoints: FishingHookHolograms and small timer/format helpers in its existing fishing package; meaningful tests and existing ordinary fishing guide. Preserve the dirty worktree and previously deployed cinematics. [Reports](reports/).

- [x] Native timer investigation and first all-variant baseline build.
- [x] Red title regression and hook-only UI/countdown implementation.
- [x] Late-canceled/expired bite reconciliation, lifecycle/counter regression tests and independent review.
- [x] Focused 21/full 131 JUnit, all-variant package and Phase9 regression.
- [x] Operator documentation/journal and focused worktree/artifact audit.
- [x] Back up/promote only the 1.21 JAR, verify remote readback and unchanged runtime YAML.

Evidence: [local verification](reports/final-verification.md), [SFTP promotion](reports/sftp-promotion.md). Pending operator checks are server restart and actual native countdown/two-line client rendering, not claimed by local tests or hashes. No commit/push or console restart performed.
