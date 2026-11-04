# Epic/Legendary cinematic result

Date: 2026-10-12, Asia/Saigon. Both ordinary catches and admin previews were requested and implemented. Local checks and SFTP promotion are complete; server restart and actual Minecraft client acceptance remain pending.

## Behavior

- Epic: 62 ticks (~3.1 seconds at 20 TPS), purple water spiral, smooth emergence, orbiting reveal and curved delivery. Native ItemDisplay also animates scale and rotation.
- Legendary/Mythic native and Legendary preview: 80 ticks (~4 seconds), gold dust halo, larger water effect and longer reveal. Preview style follows the resolved preset, including synthetic visual-only fish keys.
- Common/Rare motion remains the STANDARD path. Existing eligibility, fish selection, item delivery/rewards, cooldowns, concurrency, effects/HUD preferences and cleanup remain intact.
- Cinematic native particles and packet stage/frame/trail share a hard reservation cap of 24 individual particles per actual Paper tick. Native frame cap is also 24; packet preset config ceiling is 12 for this deployment. Reservations remain spent if emission fails. Existing non-cinematic cues/ambient particles are outside this ledger.
- All new particles use the viewer API. DUST/REDSTONE aliases carry typed DustOptions; Paper 1.20.1/1.21.11 signatures were checked from real cached APIs.

## Verification

| Check | Result and evidence |
|---|---|
| Baseline | All-variant package, 106 actual JUnit and Phase9 passed. [Report](baseline-verification.md). |
| Changed behavior | 13 focused and 114 full actual JUnit passed; eight additional tests, no baseline regression. [Report](post-implementation-verification.md). |
| Integration | Phase9, Phase11, HUD GUI, all-variant package and installed PacketEvents 2.13 signature/recovery passed. Initial isolated harness omissions were reported, corrected to match real API shapes and retained in logs. |
| Independent review | No actionable production defects. [Review](code-review.md); [particle API evidence](particle-compatibility.md). |
| Promotion scope | Baseline packaged JAR matched installed bytes/hash exactly. New JAR has 16 changed/8 added entries, zero removed/unexpected entries, all cinematic classes or the two intended default resources. [Entry diff](jar-entry-diff.json). |
| Remote config | Fresh Common/Rare presets, activation flags, config version, rarity mappings and overrides retained; only Epic/Legendary profiles and the particle ceiling changed. Real Bukkit YAML readback checks passed. |
| SFTP | Both remote targets reread and SHA-256 matched; Simulator/effects files remained byte-identical. [Durable manifest](sftp-deployment.json). |
| Runtime/client | Pending restart, native catch observation, preview movement/color, second-client privacy and lifecycle/preferences checks. No live MSPT claim. |

## Promoted targets

`/plugins/even-more-fish-2.3.6-SNAPSHOT-1.21.jar`: SHA-256 `c9db5e1500fd4f80f3b87909c1697b4ab2266346f09d26c29b2f8fbd95eae64d`.

`/plugins/EvenMoreFish/simulator/cinematics.yml`: fresh merged Epic/Legendary presets. Existing admin-preview activation is retained; all zones, AI, native hook, reward bridge and paper-gates-verified remain disabled. No database/main config/messages edits, commit, push or console restart occurred.

Remote backup: `/.emf-cinematic-polish-20261012-230354/`, with verified `before/`, `after/`, `displaced/` and manifest copies. Restore the two `before/` targets after stopping the server to roll back, then restart. SFTP scripts use process-environment credentials outside the repository.

## Apply and observe

Restart the server to load the changed Java classes. `/emf admin reload` cannot replace a restart. Aim at water within 12 blocks and use `/emf admin simulator cinematic preview epic` or `/emf admin simulator cinematic preview legendary`. `/emf hud cinematic` and `/emf admin simulator effects on` enable the viewing preference when needed. Then verify normal Epic/Legendary catches and cancellation/private visibility with real clients.

Operator details: [Vietnamese guide](../../../docs/fishing-pools-and-cinematics.md). Local journal: [implementation decisions](../../../docs/journals/261012-epic-legendary-cinematics.md).
