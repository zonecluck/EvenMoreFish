# Phase 16 Paper acceptance matrix — all live gates PENDING

| ID | Scenario | Requirement to PASS | Evidence |
|---|---|---|---|
| B16-01 | Gradle/JDK 21 | `:even-more-fish-plugin:test` + `:even-more-fish-plugin-1-21:shadowJar` run successfully | Build + JUnit log |
| G3-01 | Native cast inside zone | No native bait/loot/EXP while owned by Simulator probe | Event trace + capture |
| G3-02 | Deny, non-zone, missing region hook | Legacy behavior preserved / fails closed according to explicit policy | Trace |
| G3-03 | Cancelled event, offhand, WorldGuard | No unexpected takeover and no duplicate result | Trace |
| G5-01 | Shared/private fish | Correct per-viewer packet rendering and cleanup | Client video |
| G5-02 | PacketEvents outage + reconnect | No permanent ghost fish, bounded cleanup retry | Console + video |
| G7-01 | Correct committed `FishKey` | One item, exactly one expected stats/event path | Inventory/db trace |
| G7-02 | Event cancelled | No item, EXP, bait or stats | Test log |
| G7-03 | Full inventory / drop / double reel | No duplication, ambiguous delivery flagged | Test log |
| G15-01 | Pack loaded/declined | Correct `ItemDisplay`/vanilla visual for same fish | Two-client capture |
| G15-02 | Pack hot reload, wrong model | Clean fallback, no invisible/ghost entity | Two-client capture |
| PERF-01 | 1/10/30/50/100 actual anglers | Bounded packet, measured MSPT/heap, no sustained failure | Profiler/export |
| LIFE-01 | Reload/disable under active sessions | No stale tasks, packet entities or duplicated rewards | Trace |
| ROLL-01 | Restore previous EMF build | Legacy catches/data/shop/competition preserved | Rollback log |

**All rows are PENDING until tested on a real Paper server and client.**
Runbook: `docs/simulator/PHASE_16_PRODUCTION_VERIFICATION_RUNBOOK.md`.
