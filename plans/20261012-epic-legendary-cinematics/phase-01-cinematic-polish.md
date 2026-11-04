# Cinematic polish

Status: implementation, local verification and SFTP promotion complete; restart/client proof pending.

## Context and requirements

- [Ordinary fishing guide](../../docs/fishing-pools-and-cinematics.md)
- [Previous SFTP repair](../../../plans/20261012-emf-sftp-config-repair/reports/verification.md)
- Both actual catches and admin Epic/Legendary preview are explicitly requested.
- Preserve old timeline constructor/Frame contracts and the STANDARD path. Add explicit style selection rather than inferring tiers from edited arc sizes.
- Use zero-offset, zero-velocity motion envelopes at stage joins, private colored dust/rings/trails, modest stage sounds and bounded emissions.

## Touchpoints

Existing core files: `effects/VanillaCatchCinematicService`, `fishing/simulator/effects/PremiumCinematicTimeline`, `PremiumCinematicSettings`, `PacketCatchCinematicService`, `SimulatorEffectsService`; resources `simulator/cinematics.yml` and the previously broken `simulator/hud-gui.yml` default key. Add shared particle geometry/emission helpers within the existing effects module. Extend meaningful motion/geometry tests and operator docs.

## Execution

- [x] Capture baseline package/full-core tests and isolated phase9 regressions; review this plan before implementation.
- [x] Add explicit STANDARD/EPIC/LEGENDARY timeline style; smooth richer surface/reveal/delivery and native display pose without changing old callers.
- [x] Implement capped pure geometry and viewer-only particle emission reused by both renderers. Use a default frame callback to preserve existing StageFxSink implementations. Keep cleanup, budgets, preference guards and failure isolation.
- [x] Tune only Epic/Legendary presets; supply missing numeric defaults and quote OFF so packaged defaults remain valid.
- [x] Run focused tests, all-variant package, compatibility checks and independent review. Compare failures with baseline.
- [x] Snapshot fresh remote configuration/JAR, preserve enabled admin-preview flags and Common/Rare settings, upload verified files with rollback backup and read back hashes.

Operator acceptance outside the completed implementation/promotion: restart the server, observe both native catches and packet previews in real clients, confirm particle/sound preferences, viewer privacy and cleanup. These checks are not claimed passed by local tests or SFTP.

## Risks and rollback

Particle API aliases must work on core 1.20 and installed 1.21.11. Packet preview uses vanilla fish movement while actual catches also animate ItemDisplay scale/rotation. Keep budgets before emission and never broadcast particles. Restore backed-up JAR/preset file after stopping server to roll back; runtime interpolation/private visibility needs the user's real client after restart.
