---
date: 2026-10-17
---
# Fish item compatibility and transparent hook holograms

Context: the real `Fish` item finalizer failed with `NoSuchFieldError` against the Paper 1.21.11 runtime API before the fix. Read-only SFTP inspection confirmed production runs Purpur 1.21.11 with an older installed JAR that references the removed `HIDE_ITEM_SPECIFICS` field. The ordinary native fishing route is active (`probe: false`, `LEGACY_EMF`); custom rods are required.

What changed: resolve the optional tooltip flag once through `ItemFlag.valueOf`, trying `HIDE_ADDITIONAL_TOOLTIP`, `HIDE_ITEM_SPECIFICS`, then `HIDE_POTION_EFFECTS`. On the legacy 1.20.1 API, `HIDE_ITEM_SPECIFICS` was an alias field for the enum value `HIDE_POTION_EFFECTS`, so the final fallback is necessary. If no flag exists, omit only that decoration. Fish NBT, rewards, rarity and existing contracts remain intact.

Hook holograms now disable the default background and explicitly use ARGB alpha zero. Text shadow remains enabled; client rendering still needs confirmation.

Evidence: [before](../../qa/fish-item-before.log) reproduces the finalizer failure; [after](../../qa/fish-item-after.log) passes the additional 1.21.11 runtime API finalizer compatibility test and all 155 core tests, with no failures, errors or skips. The [offline build](../../qa/fish-item-build.log) succeeds, including all three variant `shadowJar` packages. [Phase9 regression](../../qa/fish-item-phase9-regression.log) passes ten headless suites, 57 YAML resources and 18 locale diagnostics. Its generic Gradle-unverified footer is superseded by the separate successful build log; real server/client gates remain open.

Deployment: the 1.21 JAR is staged at `plugins/update/even-more-fish-2.3.6-SNAPSHOT-1.21.jar`; readback SHA-256 matches the local artifact (`2ea4bed6edc36b594d2b3e1d64d70ea1299fdceff4fa0cadd6bc935ee33bc386`). The old JAR is backed up under `.ledat-backups/emf-fishing-fix-20261017-105619/`; the installed JAR and four checked runtime YAML files are unchanged. See [staging verification](../../qa/fish-item-sftp-verification.json). Before activation, rollback means removing this staged update; after activation, restore the backed-up JAR while stopped and restart.

Follow-up: the user confirmed failures with both custom and ordinary rods. Set only production `fishing.require-custom-rod` to `false` so both rod types may use ordinary EMF fishing. Backed up the authored config under `.ledat-backups/emf-rod-policy-20261017-110901/`; YAML semantic comparison and remote byte readback confirm every other value is unchanged. The server lacks overwrite support for SFTP POSIX rename, so promotion used a backed-up rename with rollback on failure. See [rod policy verification](../../qa/fish-item-rod-policy-verification.json).

Next: clean restart, production catches and client hologram rendering remain pending. The JAR is still staged in `plugins/update`; a config reload alone cannot apply its binary fix. No external publication or sharing was performed.
