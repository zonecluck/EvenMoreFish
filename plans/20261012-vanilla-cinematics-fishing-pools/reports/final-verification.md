# Final local verification

Date: 2026-10-12. Worktree: `C:/Users/hp/IdeaProjects/MineBox/EvenMoreFish`. Source implementation and operator documentation complete; no commit or deployment performed.

| Check | Result | Evidence and limits |
|---|---|---|
| Focused Gradle JUnit | Passed, 21 tests; 0 failed, 0 skipped | 3 policy, 13 pool and 5 timeline tests. Actual Gradle/JUnit execution; [log](../../../qa/focused-gradle.log). |
| Full core JUnit | 89 tests: 87 passed, 2 failed | Existing `SingleSlotDeliveryPlannerTest` harness failures: `ItemStack.isSimilar` accesses null `Bukkit.server`. Full suite is not green; tests were not hidden or weakened. [Log](../../../qa/full-core-tests.log); XML preserved in ignored `qa/out-full-test-results`. |
| All-variant compile/package | Passed | `gradlew --project-cache-dir qa/gradle-cache build -x test`, all 1.20/1.21/26.1 variants and addons; [log](../../../qa/package-gradle.log). Tests explicitly skipped for packaging only; separate test results above remain applicable. |
| JAR content verification | Passed | All three variants contain required cinematic/pool classes, `NativeFishingContext`, pool store, configuration and `emf.admin.pool`; Simulator/reward master flags remain false. |
| Headless smoke | Passed | Phase11, Phase9 and Phase16 plus 14 direct policy checks; [commands/log links](smoke-tests.md). Harnesses do not establish real server/client behavior. |
| Source review | Passed after fix | [Review](code-review.md). Mixed-case imported YAML deletion fixed through original-key resolution and real-file persistence regression tests; reviewer reports no remaining defects. |
| Static release gate | Blocked as designed, exit 2 | Manifest evidence and human signoff still pending; [smoke report](smoke-tests.md). No production-ready Simulator claim. |
| Paper/client acceptance | Pending | No live native hook wait, Lure, radius rendering, scene interpolation/privacy, reward semantics or lifecycle/load certification. |

## Built artifacts

Artifacts are local build output under the matching `even-more-fish-plugin-1-20/build/libs/`, `even-more-fish-plugin-1-21/build/libs/` and `even-more-fish-plugin-26-1/build/libs/` directories; these hashes establish package identity, not deployment or runtime proof.

| Variant | SHA-256 |
|---|---|
| `even-more-fish-2.3.6-SNAPSHOT-1.20.jar` | `d60320c2b38c8feb86494cafa238499468580cb7a08b5b777b5aa13eee4615a4` |
| `even-more-fish-2.3.6-SNAPSHOT-1.21.jar` | `e3bc931e93121396da537c67dad666e0b20050301aecb0f8e62a96b10fc049d7` |
| `even-more-fish-2.3.6-SNAPSHOT-26.1.jar` | `564408d04682efa6fc9d8b69928cf6fc746cfb6c89ce30bbf4a48ef515797bfa` |

Focused checks support local implementation correctness. They do not complete production acceptance. The [plan](../plan.md) and [phase](../phase-01-implementation.md) retain explicit pending runtime/release items.

Runtime checklist: verify all three create argument forms and permissions; mixed-case imported YAML remove/restart; invalid reload retention and failed save; water versus land, world/height/overlap boundaries; Lure/custom rod/buff and leave/re-entry wait behavior; canceled casts/catches; loaded-chunk bounded rings; Common/excluded/custom rarities; item delivered once; profile/cooldown/concurrency fallback; private rendering and smoothness; new cast, distance, quit/world, reload and disable cleanup. Rollback uses the flags documented in the [admin guide](../../../docs/fishing-pools-and-cinematics.md).
