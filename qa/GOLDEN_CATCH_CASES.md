# Golden catch cases — baseline regression checklist

> Status: **Not run on Paper**. Mark with result/server version/console logs before accepting Gate G1.

| ID | Scenario | Expected result |
|---|---|---|
| C01 | Vanilla rod, ordinary lake, fish caught | Existing EMF fish item and stats exactly once |
| C02 | Vanilla rod, fishing outside allowed world | No custom EMF fish |
| C03 | Vanilla rod, fishing outside allowed region | No custom EMF fish |
| C04 | Fish catch in active competition | Entry and reward logic unchanged |
| C05 | `only-fish: true`, non-fish vanilla treasure | Preserve native treasure |
| C06 | `only-fish: false`, non-fish vanilla treasure | Apply configured EMF catch policy |
| C07 | EMF fishing bait loot chance = 100% | Give bait item once, no fish stats |
| C08 | Baited normal rod catches eligible fish | Correct weighted modifier, 1 charge on accepted catch |
| C09 | Baited custom rod `allow-baits: true` | Apply bait AND rod rarity/fish restrictions |
| C10 | Baited custom rod `allow-baits: false` | Ignore bait as configured |
| C11 | Force-cancel `EMFFishCaughtEvent` | No fish reward, effects, bait consumption, metrics or DB report |
| C12 | Force-cancel `EMFFishHuntEvent` | No EMF hunt report/reward |
| C13 | Region weight Common = 0.5 | Effective weight halved, not truncated |
| C14 | Region weight Rare = 1.8 | Effective weight multiplied by 1.8 |
| C15 | Region weight Legendary = 0 | Legendary excluded except explicit admin override |
| C16 | Cast rod A, switch to rod B before pull | No custom EMF bonus from rod B |
| C17 | Cast rod, only durability changes on pull | Normal custom EMF catch allowed |
| C18 | Rod with bait replaced during cast | Old rod snapshot not accepted |
| C19 | Rod in offhand; normal reel | Snapshot resolves correct offhand item |
| C20 | Inventory full at catch | Catch item remains in world per configured policy |
| C21 | Give fish straight to inventory enabled | Single item delivery, native item removed |
| C22 | Change world, disconnect, reload | No stale rod snapshots, hooks or GUI states |
| C23 | Player joins with cold user DB cache | Join does not synchronously query JDBC |
| C24 | Cancelled fish event when DB enabled | Zero report rows |
| C25 | Reconnect quickly during async preload | Cached user ID not left orphaned |
| C26 | DB save fails/retries while catches happen | Pending dirty revisions retained, no silent loss |
| C27 | Reload rarity/bait/rod files | No persistent gameplay state corruption |
| C28 | Fish hunting (entity kill) | Existing loot/events continue to work |
| C29 | Double catch + EXP modifier | No duplicated event, multiplier applied once |
| C30 | 30 concurrent anglers with MySQL latency | Observe tick time/DB queue, no main-thread DB calls |
