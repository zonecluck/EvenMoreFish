# Phase 9.1 — Staging / rollback notes

This release contains **source-level safety improvements only**, not a completed Simulator release.

### What changed

- Failed or thrown virtual fish `spawn()` is no longer presumed never to have reached the client.
- A bounded retry ledger tracks compensating `destroy` attempts per viewer and entity ID for both ambient fish and cinematic fish.
- If PacketEvents fails while the preview is shutting down, an unresolved cleanup ledger is retained and preview restart is denied until the cleanup can be confirmed or affected clients disconnect/change dimension; an already-tripped PacketEvents adapter requires recreation, not merely a config reload.
- Catch delivery no longer uses `addItem()` followed by dropping unpredictable leftovers; it attempts one complete storage-slot write (with readback) or a single whole-stack world drop.
- Admin `/emf admin simulator status` shows pending cinematic destroy IDs.

### Recommended staging steps

1. Back up the entire server and the current EMF plugin/configs. Never overlay production jars to test this source-only build.
2. Run `./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon` on a machine with Gradle distribution and repository access.
3. Launch staging Paper 1.21.4+ with external PacketEvents. Verify both plugins enable without dependency errors.
4. Use an isolated simulator zone and admin-only preview; keep reward bridge disabled pending Gate G3/G5/G7.
5. Follow `qa/PHASE_091_PAPER_TEST_MATRIX.md`. Log all failures with server/client version, reproducible steps and packet traces.
6. If any entity/packet ownership anomaly occurs: `/emf admin simulator off`, and reconnect affected client if PacketEvents became inaccessible. A tripped adapter stays unavailable until the plugin/server is restarted with healthy PacketEvents. Do not assume backend failures can be cleaned while the backend is down.

### Rollback

1. `/emf admin simulator off` if possible; stop server cleanly.
2. Restore the previously working EMF JAR and backed-up config/data.
3. Restart server with Simulator settings disabled and verify normal vanilla EMF catches/competition.
4. Do not delete fish or player data as a form of rollback.

**G3, G5, G7 and G9 are open.** No Folia or public-player-ready claim.
