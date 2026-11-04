---
date: 2026-10-12
---
# Epic and Legendary cinematic polish

Context: presentation work for ordinary EMF catches and admin previews; [operator guide](../fishing-pools-and-cinematics.md).

What happened: native and packet renderers select explicit STANDARD, EPIC or LEGENDARY styles. Packet style follows resolved preset object identity, so synthetic preview FishKeys and value-identical profiles cannot misclassify the scene.
Native Epic lasts 62 ticks; Legendary lasts 80 ticks. Typed `DustOptions` gives private purple/gold particles through `Player.spawnParticle`, with `DUST`/`REDSTONE` compatibility aliases.
A shared ledger caps cinematic emissions at 24 individual particles per actual Paper tick. Both renderers reserve before sending; failures retain reservations. Generic cues and ambient effects are outside this ceiling.

Reflection: STANDARD motion, reward/inventory contracts, preference gates and existing display/packet cleanup lifecycle remain intact. The configured packet ceiling intentionally increases from 8 to 12.
Evidence: actual Gradle/Paper compilation, 114 core tests, 612-source syntax scan, all-variant packages and installed PacketEvents 2.13 signature/recovery harness passed; [verification](../../plans/20261012-epic-legendary-cinematics/reports/post-implementation-verification.md).

Decision: preserve the initial isolated harness failures. Missing `Color`, `NamespacedKey.minecraft` and `Enchantment.getByKey` scaffolding was corrected to match the real API; smoke suites then passed without weakening production checks.
Source review found no actionable defect; [review](../../plans/20261012-epic-legendary-cinematics/reports/code-review.md) and [particle API evidence](../../plans/20261012-epic-legendary-cinematics/reports/particle-compatibility.md) support the implementation, not live rendering claims.

Next: SFTP upload is not yet confirmed. After verified delivery and restart, obtain real Paper/client evidence for appearance, privacy, cancellation, cleanup and MSPT. AgentWiki publishing was skipped because external sharing is outside the requested task.
