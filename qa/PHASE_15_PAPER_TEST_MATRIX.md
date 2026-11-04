# Phase 15 — Paper / PacketEvents Runtime Test Matrix (Not Yet Run)

| ID | Scenario | Expected | State |
|---|---|---|---|
| B01 | Compile real Gradle variant | `compileJava`, test, shadowJar success | ❌ Not run (DNS blocker) |
| P01 | Plugin enable with models.yml default | No config error, models off, EMF vanilla works | ⬜ |
| P02 | Malformed item-model/UUID reload | Reject new config, preserve previous snapshot | ⬜ |
| P03 | Resource-pack UUID match + success event | Selected fish renders ItemDisplay for that viewer | ⬜ |
| P04 | Wrong pack UUID successful | Vanilla fish remains | ⬜ |
| P05 | Client declined or download failed | Vanilla fallback, no ghost entity | ⬜ |
| P06 | Mixed A(pack)/B(no pack) | One FishAgent, two per-viewer visuals, no privacy leak | ⬜ |
| P07 | Pack changed/rejected mid-swim | Destroy → spawn correct visual with no duplicate | ⬜ |
| P08 | Packet spawn succeeds, metadata fails | Orphan ledger retries destroy; no ghost fish | ⬜ |
| P09 | Destroy packet fails | Retry before any replacement spawn | ⬜ |
| P10 | Custom model not present in pack | Document missing-texture behavior; rollback | ⬜ |
| P11 | Private fish viewer with pack/not pack | Never appears to wrong viewer | ⬜ |
| P12 | Custom model with schooling/path AI | Smooth rotation/movement and agent authority | ⬜ |
| P13 | Legendary cinematic on success | Same model, no second catch/reward | ⬜ |
| P14 | Quit/world switch/chunk unload/reload | No leftover client entity | ⬜ |
| P15 | 1.21.1 client / ViaVersion mixed client | Vanilla fallback, no protocol crash | ⬜ |
| P16 | 30/50/100 clients stress | No sustained TPS/MSPT/packet spike; packet cap obeyed | ⬜ |
| G3 | Real vanilla hook and loot suppression | No duplicate XP/items | ⬜ |
| G5 | Real PacketEvents rendering | All model IDs and metadata valid | ⬜ |
| G7 | Catch/rod/bait/competition/database | Exactly one logical result | ⬜ |

**Ghi chú:** Bộ headless mock đã đạt không đánh dấu các ô Paper runtime ở trên.
