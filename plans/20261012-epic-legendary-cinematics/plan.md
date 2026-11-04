---
title: Epic and Legendary cinematic polish
status: completed
priority: P2
effort: medium
---
# Epic and Legendary cinematic polish

Date: 2026-10-12. Implementation, local verification and SFTP promotion complete. Server restart and real-client acceptance remain pending operator checks.

## Scope and acceptance

Improve both ordinary rare-catch ItemDisplay scenes and admin packet cinematic previews. Epic uses a purple spiral, a smooth water exit and an orbiting reveal; Legendary uses a longer gold reveal, a larger halo and a curved delivery. Common/Rare paths retain their existing motion. All particles remain viewer-private, capped across concurrent scenes and respect particle/effects/profile preferences. Existing rewards, rarity selection, hooks, inventory, permissions, configuration versions and Simulator reward gates remain unchanged.

## Phases

1. [Implement shared motion and private particle effects](phase-01-cinematic-polish.md).
2. Verify baseline and focused regressions, compile/package all variants and review lifecycle/compatibility.
3. Back up and selectively promote the 1.21 JAR and Epic/Legendary preset configuration through the already authorized SFTP connection. Restart/client proof is a separate pending runtime check.

Dependencies: installed PacketEvents 2.13 compatibility; Java 21/Paper APIs; existing dirty worktree must be preserved.

Reports: [reports/](reports/). Completion requires local checks and SFTP readback evidence; never substitute these for actual client rendering.

Result: [final verification and promotion](reports/final-verification.md). Focused 13/full 114 JUnit passed; all-variant package, Phase9/11/HUD and installed PacketEvents 2.13 checks passed. JAR entry comparison contained only cinematic changes. Two remote targets were backed up, promoted and hash-verified. No commit/push or console restart performed.
