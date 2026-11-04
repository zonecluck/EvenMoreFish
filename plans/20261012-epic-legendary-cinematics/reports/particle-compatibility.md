# Particle API compatibility and shared budget

Date: 2026-10-12. Status: PASS for inspected source/API contracts; Paper runtime and client rendering remain unverified.

## Evidence inspected

Read `AGENTS.md`, this plan and phase file, `CinematicParticleEffects.java`, `CinematicParticleBudget.java`, native `VanillaCatchCinematicService`, packet `SimulatorEffectsService` / `PacketCatchCinematicService`, and the existing `CinematicParticlePatternTest`. No source/configuration edits or Gradle execution were performed for this check.

Actual cached API artifacts inspected with JDK 21 `javap -p -s -c -classpath <jar>`:

| Artifact | Cache content directory | SHA-256 |
| --- | --- | --- |
| `paper-api-1.20.1-R0.1-SNAPSHOT.jar` | `71680be2f2e80f25e20ae4b49ebd84539d306a44` | `161ecf24e6ffb325a79eb7eb04904419c2b80f602672e4758785e9389e9038d1` |
| `paper-api-1.21.11-R0.1-SNAPSHOT.jar` | `20961a0e95ea65d0af88dffc9b1a90421b4dfeca` | `c577b181c11a8674310e56c92a91e31c010b7f04c9bd10b91c3be18374401070` |

Cache root: `C:/Users/hp/.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/<version>/<content-directory>/`.

## API result

- Paper 1.20.1 exposes `REDSTONE`, with its static initializer passing `Particle$DustOptions.class` to the enum constructor (bytecode offsets 433–444). Paper 1.21.11 exposes `DUST` and registers the same data class (offsets 493–507). `particle("DUST", "REDSTONE")` therefore selects the correct enum constant without linking a removed enum field.
- Both APIs expose `Particle.DustOptions(Color, float)` with descriptor `(Lorg/bukkit/Color;F)V`. The configured RGB values and size `0.8f` are valid constructor inputs.
- Both expose `Player.spawnParticle(Particle, Location, int, double, double, double, double, T)` with identical erased descriptor `(Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V`. It is abstract in 1.20.1 and default in 1.21.11; the call contract remains present. The overload without data is also present in both.
- `ENCHANTMENT_TABLE` exists in 1.20.1; `ENCHANT` exists in 1.21.11. The corresponding string fallback resolves correctly. `BUBBLE_POP` exists in both, so the secondary `WATER_BUBBLE` name is not needed on either inspected version.
- `Bukkit.getCurrentTick()` has descriptor `()I` in both APIs and delegates to `Server.getCurrentTick():()I`. Passing its result to the ledger's `long currentTick` uses normal widening and introduces no signature mismatch.

No concrete API incompatibility was found for these two artifacts. This is direct API/bytecode evidence, not a claim that a running server or client rendered colored dust successfully.

## Shared ceiling and failure accounting

- `CinematicParticleEffects` owns one static `CinematicParticleBudget(24)`. All reservations use the actual `Bukkit.getCurrentTick()` rather than the native scene counter or Simulator counter. Two callers in the same server tick therefore share the same ledger.
- The ledger resets only when the supplied server tick changes, clamps negative requests to zero, takes the remaining allowance and increments consumed slots immediately. There is no release/refund operation.
- New Epic/Legendary `emit()` reserves the entire permitted batch before its first `Player.spawnParticle` call. Each iteration uses count `1`. A partial send or exception returns the reserved count and failure without refunding slots.
- Native STANDARD particles reserve their requested count and increment the local frame count before sending. Native Epic/Legendary frames use the shared emitter. Packet cinematic stage cues reserve and increment the local count before their loop; trails reserve one slot before sending; rich frames use the shared emitter and account for its returned reservation. Local renderer/config limits can further reduce emissions, but cannot increase the shared 24 ceiling.
- Packet `safelyStage`, `safelyFrame` and `safelyTrail` catch `RuntimeException | LinkageError` and increment `stageCueFailures`. Native frame handling catches the same failure types; rich emitter failures are returned for warning. Failed particle sends do not change reward or packet cleanup logic in these inspected paths.
- Particle sends here target `Player.spawnParticle`; these cinematic paths do not call `World.spawnParticle`.

The existing unit test `bothRenderersShareTheReservationAndFailureDoesNotFreeSlots` checks the pure ledger's 8 + 12 + 4 reservations, exhaustion, negative requests and next-tick reset. It does not simulate actual Bukkit send failures; source ordering supplies that specific evidence. Test execution belongs to the separate verification task.

## Boundaries

The ceiling covers native cinematic particles plus packet cinematic stage/trail/frame particles. Existing generic Simulator `cue()` particles, ambient encounters, pool markers and legacy `AnimationManager` effects do not reserve through this ledger. Do not describe the result as a global ceiling for every EvenMoreFish particle.

The ledger is deliberately unsynchronized and assumes the documented Paper owner-thread execution. This review does not establish Folia compatibility, client appearance, actual server MSPT or live preference behavior. A real restarted Paper/client smoke check remains required.
