# Phase 10B/11 — Paper staging acceptance test matrix (NOT EXECUTED)

Record Minecraft/Paper/PacketEvents versions, Java, OS, world, ping, configured zone, viewer count, timings and screen recordings for each PASS.

| # | Staging test | Expected | Status |
|---|---|---|---|
| G3-1 | Native bite and loot suppression when AI controls hook | No vanilla duplicate loot/EXP | NOT RUN |
| G3-2 | WorldGuard/RedProtect canceled fish event | No Simulator takeover/reward | NOT RUN |
| G5-1 | Vanilla packet fish spawn/teleport/destroy with PacketEvents | No invisible/ghost fish | NOT RUN |
| G5-2 | Packet metadata fails midway | Orphan ID tracked/retried | NOT RUN |
| G5-3 | One player switches world/logs out during cinematic | All client fish removed | NOT RUN |
| G5-4 | Server reload during scene before first spawn | No entity remains | NOT RUN |
| G5-5 | Viewer moves beyond max distance | Scene cleanup | NOT RUN |
| G7-1 | Successful catch of a visible rare fish | Exact FishKey item + 1 reward | NOT RUN |
| G7-2 | `EMFFishCaughtEvent` cancellation | No item/bait/stats | NOT RUN |
| G7-3 | Rapid double right-click | Single completion/no dupe | NOT RUN |
| G7-4 | Inventory full or drop fails | No partial delivery/duplicate history | NOT RUN |
| FX-1 | Common profile | 16-tick short arc, no huge title spam | NOT RUN |
| FX-2 | Rare profile | Smooth water breach/reveal | NOT RUN |
| FX-3 | Epic profile | Distinct vortex/reveal sounds | NOT RUN |
| FX-4 | Legendary profile | 6 stages, ~70 ticks, no teleport jumps | NOT RUN |
| FX-5 | Myths/custom overrides | Correct tier mapped from real FishKey | NOT RUN |
| FX-6 | Legacy BALANCED profile | Old short arc, no premium V2 | NOT RUN |
| FX-7 | REDUCED_MOTION / OFF | No premium scene or excessive FX | NOT RUN |
| FX-8 | `/emf admin simulator cinematic preview legendary` | Visual-only, no item/stat event | NOT RUN |
| FX-9 | Stop, quit, world change, new cast | Scene removed, no stuck entity | NOT RUN |
| FX-10 | Packet transport unavailable | Visual rejected; reward unaffected | NOT RUN |
| GUI-1 | `/emf hud` when GUI disabled | Text fallback | NOT RUN |
| GUI-2 | GUI open, click each preset | Updated profile, saved after relog | NOT RUN |
| GUI-3 | Shift/double/number-key/hotbar/drag/bottom click | Never moves config items | NOT RUN |
| GUI-4 | Old GUI closing after new open | Does not erase new GUI session | NOT RUN |
| GUI-5 | Reload GUI while open | Graceful close/cleanup | NOT RUN |
| GUI-6 | Invalid material, duplicate slot, sound key | Reload rejected; old settings retained | NOT RUN |
| PERF-1 | 1/10/30/50/100 viewers with rare scene | Capture Spark MSPT/p95 packet budget | NOT RUN |
| PERF-2 | 4 concurrent cinematic + packet cleanup retry | No starvation/dupe, bounded queue | NOT RUN |
| DEP-1 | Paper 1.21.4+ plus core 1.20.1 API compatibility | Full Gradle compile and JUnit PASS | BLOCKED — Gradle distribution DNS |

**Release remains blocked** until all G3/G5/G7 and build gates have actual recorded evidence.
