# Title regression reproduced

Date: 2026-10-12. Production `FishingHookHolograms` remained unchanged during this run.

Added only `even-more-fish-plugin/src/test/java/com/oheers/fish/fishing/FishingHookHologramsTest.java`. It constructs the real hologram service using actual SDK mocks for the hook, world, player, display and scheduler. The world spawn answer invokes the real initialization consumer on the TextDisplay mock, and the captured real updater runnable runs manually. Hook locations return fresh clones so location mutation does not accumulate.

`gradlew.bat --project-cache-dir qa/gradle-cache :even-more-fish-plugin:test --tests '*FishingHookHologramsTest' --rerun` compiled and ran 5 tests: 4 expected regression failures, 1 passing lifecycle test. [Log](title-regression-red.log), [XML](title-regression-red-results/).

| Test | Actual outcome and cause |
|---|---|
| Bite animation updates display without sending titles | Failed: `Player.showTitle` invoked from bite updater. Display update and float-position checks passed before title assertion. |
| Missed transition does not send/clear titles | Failed: `Player.showTitle` invoked from missed path; existing transition also clears title. |
| Explicit removal does not clear unrelated titles | Failed: `Player.clearTitle` invoked while removing display. |
| Clear does not clear unrelated titles | Failed: `Player.clearTitle` invoked during bulk display cleanup. |
| Invalid hook removes display and stops idle updater | Passed: display removed and scheduler canceled exactly once, including a second idle tick. |

Tests verify no `showTitle`, `sendTitlePart` or `clearTitle` calls after correction, while preserving real TextDisplay behavior and lifecycle assertions. No production implementation, other tests or configuration were changed by this agent.

Two initial test compilation attempts exposed overloaded `World.spawn` matcher typing, then the Paper 1.20 SDK's `org.bukkit.util.Consumer` requirement. These were corrected only in the new test; [initial](title-regression-test-compile-initial.log) and [second](title-regression-test-compile-second.log) compiler logs are retained. The red results above are genuine title-behavior failures after successful SDK compilation, not harness compilation failures.
