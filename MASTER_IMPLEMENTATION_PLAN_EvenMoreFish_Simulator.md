# 🎣 EvenMoreFish Simulator V2 — MASTER IMPLEMENTATION PLAN

> **Loại tài liệu:** Requirement Specification + Technical Design + Phased Implementation Backlog + Production Acceptance Gates  
> **Ngày lập:** 2026-10-11  
> **Nguồn cơ sở:** `EvenMoreFish.zip` (Java source, Gradle Kotlin DSL, resources, API, test), `RozenFishing-1.0.0-SNAPSHOT.jar` (tham khảo hành vi thông qua bytecode; **không** sao chép mã nguồn), `FISHING_PLUGIN_COMPARISON_REVIEW.md`.  
> **Trạng thái:** **PLANNING ONLY — CHƯA IMPLEMENT CODE**  
> **Quyết định đã chốt:** Q1=B, Q2=C, Q3=A, Q4=B, Q5=A.  
> **Phạm vi triển khai khuyến nghị:** nhánh Paper 1.21.x (`even-more-fish-plugin-1-21`), Java 21. Giữ nguyên các module 1.20/26-1 trong repo; không hứa hỗ trợ đầy đủ cho chúng ở đợt đầu.

---

## 0. Executive summary

**Mục tiêu:** Nâng cấp EvenMoreFish (EMF) từ hệ thống chọn và thay loot sau khi `PlayerFishEvent.CAUGHT_FISH` thành **Fishing Simulator** theo vùng. Trong khu Simulator, người chơi nhìn thấy cá ảo bằng packet bơi thực sự trong hồ, thấy chúng phát hiện mồi và tiến tới phao, chờ thời điểm cắn rồi kéo lên. Ngoài các vùng đó, toàn bộ cơ chế **EMF vanilla-enhanced** hiện tại vẫn tiếp tục hoạt động.

### 0.1. Five non-negotiables

1. **Không phá EMF:** giữ cá, rarity, requirement, custom rods, bait, competition, fish journal, sell shop, rewards, DB reports, permissions, hook và public API hiện tại.
2. **Visual = truth:** một con cá được target phải mang danh tính cá EMF thực, không đổi kết quả ngẫu nhiên ở thời điểm nhận thưởng. Ở mode cá riêng, danh tính vẫn do server quyết định.
3. **Một lần câu — một kết quả:** trong một phiên câu chỉ có **một** flow sở hữu và trao loot: `VANILLA_EMF` **hoặc** `SIMULATOR`. Không phát thưởng kép từ `PlayerFishEvent` + custom completion.
4. **Fail safely:** PacketEvents thiếu/hỏng, region hook vắng, reload hay quần thể cá quá tải không được làm server crash, spawn item sai, bypass bảo vệ vùng hay mất dữ liệu người chơi.
5. **Dễ quản trị:** mọi budget, vùng nước, mật độ, loại hiển thị, animation, hành vi cá và thông báo đều nằm trong config; có metrics, diagnostic commands, migration và rollback.

### 0.2. Scope quyết định

| Nhu cầu | Quyết định | Phạm vi |
|---|---|---|
| Chỉ một số vùng có Simulator | Q1 **B** | World/region được cấu hình; ngoài vùng giữ EMF kiểu cũ |
| Cá packet hiển thị | Q2 **C** | Cá chung trong hồ + cá đặc biệt riêng từng viewer |
| Cách kéo | Q3 **A** | Cá tiến tới phao, cắn và kéo đúng thời điểm; **không** bắt buộc tension/minigame mới |
| Model cá | Q4 **B** | Cá vanilla làm mặc định; custom model/`ItemDisplay` từ resource pack là optional |
| Bảo toàn tính năng | Q5 **A** | Chỉ nâng cấp engine; không tự thêm level, prestige, minion, currency mới |

### 0.3. Định nghĩa hoàn thành

- Khi `simulator.enabled: false`, chức năng hiện tại không đổi (ngoại trừ các bugfix được test regression).
- Trong zone Simulator, cá packet có vòng đời và di chuyển; cùng một cá chung hiển thị nhất quán cho nhiều player trong bán kính; cá riêng chỉ tồn tại trên màn hình chủ sở hữu.
- Player chỉ nhận một loot đã được xác định trước; không có vanilla item/EXP phát sinh ngoài reward đã kiểm soát.
- `EMFFishCaughtEvent` của EMF tiếp tục được phát **đúng một lần** cho successful EMF fish catch; competition, stats, reward, journal và PAPI có kết quả như trước.
- Trên Paper profile định sẵn, chạy test chức năng + stress + recovery và đo được packet/tick, sessions, active agents, scan time, MSPT; không tuyên bố hiệu năng khi chưa đo.

---

## 1. Requirement Summary

### 1.1. Mục tiêu sản phẩm

- Biến câu cá thành một hoạt động **có quan sát và phản hồi**, không đơn thuần đứng chờ vanilla RNG.
- Vẫn sử dụng toàn bộ nội dung cá EMF đã cấu hình trong `rarities/*.yml`.
- Server có thể mở "fishing lake" chuyên dụng, trong khi người chơi ở Survival/Skyblock thông thường vẫn câu như trước.
- Admin có thể tăng/giảm mật độ và độ khó phản ứng mà **không chỉnh Java**.

### 1.2. Player flow đã thống nhất

1. Vào một khu câu cá Simulator; nếu có viewer, cá ảo bơi dưới mặt nước.
2. Cầm cần hợp lệ (vanilla/custom rod) có bait hoặc không; thả phao **thật** của Minecraft xuống nước trong zone.
3. Engine giữ `FishingSession`, chọn cá hợp lệ trong đàn; cá chú ý phao và bơi tới.
4. Khi cá đủ gần, chạy cue `BITE`: bọt nước, âm thanh, holo/title/actionbar tùy config; mở một **reaction window**.
5. Kéo **đúng thời điểm**: thành công → cá bị kéo lên, chạy effect, trả đúng item EMF + reward/XP theo rule; kéo sớm/quá muộn → thất bại, cá bỏ chạy/thoát, không nhận cá.
6. Cá đã bắt thành công biến mất khỏi tất cả viewer nếu là `SHARED`, hoặc chỉ chủ sở hữu nếu là `PRIVATE`.
7. Người chơi rời vùng, đổi world, logout, phá phao, thay cần không hợp lệ → kết thúc session, cleanup; không nhận reward muộn.

**Không yêu cầu:** thanh tension/mini-game nhiều giai đoạn, câu theo nút liên tiếp, rod leveling, minion, prestige, currency mới.

### 1.3. Admin flow mặc định

- Bật/tắt Simulator toàn plugin và từng zone.
- Cấu hình world/cuboid/WorldGuard/RedProtect zone; xem zone hiện tại tại vị trí người chơi/phao.
- Kiểm tra health/budget, danh sách hồ, count cá/shared/private/sessions, reason fallback.
- Reload cấu hình **atomic**, không làm mất config trước khi validate.
- Debug một phiên câu theo UUID/player/zone, không spam console.
- Có emergency `simulator off` hoặc `safe-mode` chuyển **lượt câu mới** về EMF vanilla.

### 1.4. Các hệ thống giữ nguyên

| Hệ thống | Mức độ giữ | Ghi chú |
|---|---|---|
| `FishManager` / rarity / fish definitions | Giữ | Dùng trực tiếp nguồn dữ liệu, không tạo catalog trùng |
| `Rarity.getFishList()` / `Fish.createCopy()` | Giữ | Tạo bản riêng khi commit, không mutate template fish của agent |
| `RodManager` / `CustomRod` | Giữ + adapter | Luck/lure/double/exp và allowed-fish được chuyển sang simulator đúng nghĩa |
| `BaitManager` / `BaitNBTManager` | Giữ + adapter | Bait làm cá bị thu hút, hỗ trợ weight; không tiêu hao khi hụt |
| `Competition` / leaderboards | Giữ | Simulator fish thành công vẫn nộp kết quả vào competition đang chạy |
| Catch messages / broadcasts / sound | Giữ + mapping | Không gây double notification; animation mới là từng layer riêng |
| Shop / Sell / Journal / Stats / PAPI | Giữ | Dùng item/metadata EMF hiện tại để không phá lưu trữ |
| `HuntingProcessor` | Giữ | Cá ảo packet **không phải** entity thật để săn/đánh |
| API cũ / addon / 1.20/26-1 | Bảo toàn source/API | Đợt 1 build/test Paper 1.21.x; chưa hứa behavior mọi version |

### 1.5. Out of scope

- Không triển khai progression mới, bait fusion, câu tự động, minion, Prestige, fishing skill tree.
- Không fork hoặc decompile-copy logic/asset của RozenFishing vào source EMF; chỉ sử dụng nó làm **benchmark UX/behavior**.
- Không thiết kế Folia support chính thức trong phase đầu; kiến trúc vẫn phân tách scheduler để dễ hỗ trợ sau.
- Không ép player dùng resource pack; không thay đổi tất cả item cá hiện có.
- Không mở rộng thành network-shared population cross-server trong đợt đầu.

### 1.6. Assumptions cần ghi trong project

- World/region sẽ chỉ định qua `simulator.yml`; khi không khai báo zone hợp lệ thì không bật Simulator ở đâu.
- Khu Simulator được chọn theo **vị trí phao trong nước**, không phải vị trí đứng của người chơi (để không lợi dụng biên region).
- Player phải có quyền dùng câu cá theo các rule EMF có sẵn; ngoài ra chỉ cần `emf.simulator.use` nếu admin bật permission gate.
- Với `SHARED`, server quyết định danh tính của cá khi sinh ra. Với `PRIVATE`, server quyết định danh tính riêng cho viewer.
- Fallback về EMF vanilla được quyết định **lúc bắt đầu cast**, không đổi mode nửa chừng nếu engine hết cá/lag.
- Game balance ban đầu lấy từ EMF; xác suất cuối cùng trong simulator cần **đo và hiệu chỉnh** vì quần thể cá nhìn thấy không đồng nhất hoàn toàn với random-per-catch của vanilla.

---

## 2. Audit theo source EMF: điểm nối chính xác

> Các đường dẫn sau thuộc ZIP đã cung cấp. Chúng là **mốc refactor**, không phải yêu cầu xóa class. Các file/resource đã được kiểm tra; chưa build hoặc đo runtime ở phase lập kế hoạch.

| Source hiện tại | Vai trò đang có | Việc phải làm |
|---|---|---|
| `even-more-fish-plugin/.../fishing/FishingProcessor.java` | Nghe `PlayerFishEvent`, hologram, rod/permission, `CAUGHT_FISH`, thay `Item` loot | Refactor thành router `VANILLA_EMF`/`SIMULATOR`, tách xử lý event chung/riêng; chỉ một engine sở hữu session |
| `.../fishing/Processor.java` | `getCaughtItem()` gộp validate, roll bait, chọn cá, gọi EMF event, rewards, leaderboard, tạo item | Tách `Eligibility` → `FishSelection` → `CatchCommit`; giữ adapter legacy method/API |
| `.../fishing/items/FishManager.java` | Weighted rarity/fish, requirement, rod filters; vùng boost | Tái dùng catalog và weighted rules; fix multiplier thập phân; lấy region của **hook location** |
| `.../fishing/items/Rarity.java` | `getFishList()` trả `Fish::createCopy()` | Lưu `FishKey` immutable trong agent, clone `Fish` chỉ khi prepare/commit |
| `.../baits/BaitHandler.java` / `BaitNBTManager` | Bait modifiers, bait-on-rod, tiêu hao | Tách evaluate boost/attraction và consume thành hai giai đoạn, chống mất bait khi hủy câu |
| `.../fishing/rods/CustomRod.java` / `RodManager` | Lure, luck, allowed fish, double catch, exp | Snapshot rod lúc cast; validate lại trước reward; đồng bộ với bait policy |
| `.../fishing/FishingHookHolograms.java` | `TextDisplay` thật và tick updater riêng | Trong SIM zone dùng feedback packet/batched; ngoài zone giữ hologram cũ |
| `.../fishing/EMFFishListener.java` | Stats/log/report/DB theo `EMFFishCaughtEvent` | Audit cancellation và ghi DB; không đọc DB cold-cache trực tiếp trên main thread |
| `.../plugin/EventManager.java` | Register `FishingProcessor`, hunt, DB, listener khác | Register manager/router/cleanup đúng một lần, tránh 2 catch listeners |
| `.../EvenMoreFish.java` | Bootstrap/reload/shutdown plugin | Thêm lifecycle nhỏ `SimulatorModule`; init sau Fish/Bait/Rod Manager; cleanup trước khi unload catalogs |
| `.../config/MainConfig.java` | Đọc `config.yml`, `allowed-worlds`, `allowed-regions`, `region-boosts` | Giữ config cũ; thêm loader `SimulatorConfig` và vùng `ZoneResolver` tách riêng |
| `.../plugin/ConfigurationManager.java` | Loader config/locale/GUI | Validate simulator config và build snapshot; reload all-or-nothing phần simulator |
| `even-more-fish-api/.../events/EMFFishCaughtEvent.java` | Event `Cancellable`, fish/player/time | **Không đổi constructor/semantics**; dùng tiếp sau khi cá đã hoàn tất; có thể thêm event Simulator mới |
| `even-more-fish-api/.../fishing/FishingType.java` | Enum `VANILLA`, `LAVA`, `VOID` | **Không tự thêm `SIMULATOR` vào enum này**; dùng `EngineMode` riêng để bảo toàn addon/requirement behavior |
| `.../database/data/manager/DataManager.java` | Dirty cache/write-behind | Sửa race flush bằng version/generation check, đưa I/O khỏi thread xử lý câu |
| `even-more-fish-plugin-1-21/` | Build/runtime riêng cho 1.21.x | Thêm dependency, descriptor hook, test packet compatibility vào variant này |

### 2.1. Lỗi đã xác định và cần giải quyết trước Simulator

**BLOCKER/P1**

- Trong `Processor.getCaughtItem()`, `BaitHandler.handleFish()` có thể tiêu hao bait **trước** khi `EMFFishCaughtEvent` được chấp nhận. Flow Simulator không được lặp lại cách này.
- `EMFFishListener.onEMFFishCatch()` gọi `handleFishEvent()` không check `event.isCancelled()`, trong khi event là `Cancellable`. Nếu một listener khác hủy, DB report có nguy cơ được ghi trước khi quyết định cuối cùng. Phải chuyển thống kê sang điểm commit đã xác nhận hoặc listener MONITOR chỉ đọc event còn hợp lệ.
- `UserManager` / `EMFFishListener` có cold DB read trên event path; không mở rộng thiết kế đồng bộ này sang Simulator.
- Event/loot suppression chưa có engine mới: nếu không xử lý, có thể **nhận item EMF + vanilla**.

**P2**

- `Processor.chooseFish()` chỉ dùng algorithm bait khi `customRod == null`, mặc dù custom rod có `allow-baits`. Phải có behavior `CustomRod × Bait` nhất quán.
- `FishManager.getAllowedRarities()` sử dụng `(int) Math.max(1, regionBoost)`, khiến `0.5`, `1.8` không ra đúng multiplicative weight.
- `FishManager.getAllowedRarities()` lấy region từ `fisher.getLocation()`; Simulator cần dùng vị trí **phao/zone** làm context.
- `FishingProcessor.getRod()` lấy cần từ main/offhand khi kéo, có thể không phải cần lúc cast; cần snapshot hand/rod identity.
- `FishingHookHolograms` có entity `TextDisplay` và teleport/title mỗi tick, không nhân đôi hiệu ứng này trong SIM mode.

**Mốc version/config thực tế:** `config.yml` của ZIP dùng `version: 8`, `messages_en.yml` dùng `version: 13`. **Không đổi field migration cũ thành `config-version`**. Tạo schema riêng `simulator.yml` với `config-version` của Simulator và migration riêng. Các addon/build convention hiện tại phải được xác minh bằng baseline build trước khi chỉnh logic.

---

## 3. Target architecture — refactor có kiểm soát

### 3.1. Sơ đồ module

```text
EMF Bukkit/Paper Events
         │
         ▼
FishingEventRouter             (single owner; old FishingProcessor becomes thin adapter)
         │
         ├─ ZoneResolver + Eligibility → EngineMode = VANILLA_EMF
         │                                      │
         │                                      └─ Legacy EMF FishingProcessor/Resolver
         │
         └─ ZoneResolver + Eligibility → EngineMode = SIMULATOR
                                                │
                              FishingSessionManager / StateMachine
                                  │          │         │
                                  ▼          ▼         ▼
                           FishPopulation  Targeting  HookBridge
                           /WaterIndex     /Reserve   /BiteWindow
                                  │
                      PacketFishRenderer + Visibility
                                  │
                                  ▼
                       CatchCommitCoordinator (shared)
                                  │
                  ┌───────────────┼──────────────────┐
                  ▼               ▼                  ▼
              EMF Fish       EMF Event          EMF Reward/Stats
              clone/init     contract           Competition/Shop
```

**Mấu chốt:** Router chọn engine ở `FISHING`; không cho mỗi engine tự đăng ký một catch handler giành `PlayerFishEvent`.

### 3.2. Interface boundary cần có (contract, chưa code)

| Contract | Input | Output/trách nhiệm |
|---|---|---|
| `FishingEngine` | `CastContext`, event state | `start`, `handleEvent`, `cancel`, `shutdown`, không trao thưởng trực tiếp |
| `EngineSelectionService` | hook location, player, config snapshot | `VANILLA_EMF` / `SIMULATOR` / `DENY`, kèm reason |
| `SimulatorZoneResolver` | world + hook coordinates | `ZoneSnapshot` ổn định và validation result |
| `FishCatalogAdapter` | zone, player, rod, bait, requirement | Eligible fish key + effective weights; dùng `FishManager` hiện có |
| `FishPopulationService` | zone, water sample, nearby viewers | Spawn/despawn `FishAgent` dạng server-only model |
| `FishTargetingService` | hook, available agents, attraction policy | Reserved target + TTL, không mutation reward |
| `FishingSessionManager` | UUID, hook UUID, generation | Atomic state, unique session, cleanup |
| `NativeHookBridge` | `FishHook`, `PlayerFishEvent` | Chặn vanilla bite/loot theo Paper API, giao reel event hợp lệ |
| `PacketFishRenderer` | immutable agent snapshots, viewer UUID | Spawn, metadata, movement, destroy packets + budget |
| `CatchCommitCoordinator` | finished session + target key | Validate, clone fish, fire EMF event, produce one reward, stats/competition |
| `SimulatorVisualService` | session state, rarity | Particle, sound, HUD, catch animation phù hợp viewer |
| `SimulatorMetrics` | counters/histogram | Aggregated health, per-zone load, bounded debug |
| `SchedulerGateway` | task owner/world/player context | Các callback đúng thread/region ownership; chưa claim Folia |

### 3.3. Dữ liệu domain tối thiểu

**`FishKey`**: `{rarityId, fishId}` — identity bền vững; tránh giữ mutable `Fish` lâu dài.

**`FishAgent`**: `{agentId, fishKey, zoneId, worldKey, position, velocity, movementPhase, visibility( SHARED | PRIVATE ), ownerUUID?, reservedSessionId?, spawnEpoch, despawnReason}`.

**`FishingSession`**: `{sessionId, playerUUID, hookUUID, castHand, rodFingerprint, baitSnapshot, zoneSnapshot, engineMode, targetAgentId?, biteStartTick?, biteDeadlineTick?, generation, state, completedAtomicFlag}`.

**`VisualView`**: `{viewerUUID, visibleAgentIds, clientEntityIds, spawnSequence, lastPacketTick, detailLevel}` — chỉ dữ liệu hiển thị; không có quyền chọn reward.

**`CatchOutcome`**: `{sessionId, targetFishKey?, category(FISH|BAIT_LOOT|VANILLA_TREASURE|FAIL), cloneFish?, intendedQuantity, expPolicy, acceptance, failureReason}`.

**Không giữ `Player`, `World`, `ItemStack` sống lâu trong async cache**; nếu cần giữ thông tin cần thì snapshot an toàn và resolve trên đúng thread khi commit.

### 3.4. Tương thích public API

- `EMFFishCaughtEvent`: giữ nguyên package, type và ý nghĩa; phát **một lần duy nhất** khi có fish catch được chấp thuận.
- `FishingType.VANILLA` tiếp tục mô tả **kiểu câu nước thường** theo requirement hiện tại; `EngineMode.SIMULATOR` là khía cạnh khác. Không dùng tên `SIMULATOR` để phá hành vi addon kiểm tra `FISHING-TYPE: VANILLA`.
- Nếu cần integration mới, tạo read-only `EMFSimulatorSessionEvent` / `EMFSimulatorBiteEvent` (chỉ khi có use-case), hoặc event cancellable ở trước commit, không thay đổi event EMF gốc.
- Catch/hunt API cho addon tiếp tục giữ; không trigger `EMFFishHuntEvent` từ packet fish vì chúng không phải cá vật lý.

---

## 4. Simulator gameplay và state machine

### 4.1. State machine chuẩn

```text
CAST_VALIDATED
      │
      ▼
WAITING_FOR_TARGET ─── timeout/cancel ─────────► CANCELLED
      │
      ▼
TARGET_RESERVED   ─── target vanished ────────► WAITING_FOR_TARGET (bounded retry)
      │
      ▼
APPROACHING       ─── target invalid/escape ──► FAIL (or bounded retarget)
      │
      ▼
BITE_READY        ─── bite window expired ────► FAIL_MISSED
      │
      ├─ reel too early ───────────────────────► FAIL_EARLY
      │
      └─ reel in window ───────────────────────► COMMITTING
                                               │
                                  ┌────────────┴────────────┐
                                  ▼                         ▼
                           SUCCESS_COMMITTED          FAIL_CANCELLED
                                  │                         │
                                  └────────────┬────────────┘
                                               ▼
                                           CLEANUP
```

`COMPLETE`/`CANCELLED`/`FAILED` là terminal states; không được chuyển ngược về `COMMITTING` sau khi player đã logout/reload. Mỗi session gắn `generation` chống callback cũ tác động lên session mới.

### 4.2. Từng trạng thái với rule gameplay

| State | Action | Sự kiện fail | Player feedback |
|---|---|---|---|
| `CAST_VALIDATED` | Kiểm tra world/region, water, rod, permission, hook | Vùng không hợp lệ, hook không phải nước | Legacy EMF/vanilla hoặc thông báo policy |
| `WAITING_FOR_TARGET` | Fish targeting tìm agent đủ điều kiện gần phao | Không cá, hết hạn search, đổi vùng | Bọt nhẹ/holo "Đang chờ..." |
| `TARGET_RESERVED` | Ghi reservation atomic; agent chỉ một session được claim | Có ai claim trước | Tìm cá khác, không duplicate |
| `APPROACHING` | Cá quay đầu, bơi theo tuyến quanh vật cản | Bị mất nước, path fail, chunk unload | Particle chuyển động theo cá, tùy distance |
| `BITE_READY` | Lure motion, splash, sound, HUD nhắc reel | Hết bite window, player gãy/đổi cần | Cue dễ nhận ra, **không spam title mỗi tick** |
| `COMMITTING` | Claim 1 lần, revalidate, clone fish, API event, reward | Event canceled, player offline, inventory/delivery fail | Thành công/hụt theo messages |
| `CLEANUP` | Clear hook effect/session/reservation/private agent | Luôn thực hiện cả khi exception | Destroy packet/hide effect |

### 4.3. Thông số mặc định (khuyến nghị, phải chỉnh qua playtest)

- Target search distance: **9–12 blocks** quanh phao.
- Chờ cá chú ý: **2–8 giây**, chịu ảnh hưởng `rod.lure`, bait, rarity và mật độ cá.
- Approach speed: **0.07–0.16 block/tick** theo type/rarity; không bắt mọi cá di chuyển như nhau.
- Bite reaction window: **0.9–1.4 giây**; phép đo thực tế phải tính server tick/tick time, không hardcode FPS client.
- Khi player kéo quá sớm: **không thưởng**, cá hoảng và rời xa; optional hụt vẫn cho thử lại nếu còn phao và state phù hợp.
- Khi missed: reset hook/session cleanly; không tự động thưởng để tránh click macro đơn giản.
- Rare/Legendary **không ép tension bar**; khó hơn ở chờ, tốc độ, reaction window và cue tùy chỉnh.

Những con số là *starting presets*, **không phải thông số xác nhận từ RozenFishing**. Admin có thể override theo zone/rarity/fish.

### 4.4. Một fish được nhìn thấy = một fish được bắt

- Agent có `FishKey` từ khi tạo, không sinh `Fish` mutable chỉ để render.
- Mỗi shared agent dùng cùng `FishKey` và tọa độ server cho mọi viewer; người khác không thấy model hoặc rarity khác nhau cho **cùng agent**, trừ khi đổi hoàn toàn vì policy privacy/pack fallback đã được giải thích cho người chơi.
- Tại `BITE_READY`, session lưu `agentId` và `FishKey` không đổi; lúc `COMMITTING` clone fish bằng catalog theo key, tạo length/metadata từ EMF rồi trao đúng item ấy.
- Khi model mặc định chỉ có Cod/Salmon/Tropical/Pufferfish, có thể **nhiều fish ID dùng chung ngoại hình vanilla**. Không được tuyên bố là người chơi phân biệt được cả 72 loài chỉ bằng model. Có thể dùng optional name reveal/rarity glow lúc bite.
- Khi thiếu resource pack, người chơi thấy fallback vanilla fish chứ không nhận item rỗng hoặc cá invisible.

---

## 5. Region routing — Simulator ở đúng nơi, không bypass claim

### 5.1. Ba loại zone hỗ trợ

1. `WORLD`: toàn bộ một world; áp dụng `enabled`, mật độ, visuals, fish pool theo world.
2. `CUBOID`: vùng tọa độ min/max có tên, không phụ thuộc plugin bảo vệ vùng.
3. `WORLDGUARD_REGION` / `REDPROTECT_REGION`: map theo ID/name region từ plugin tương ứng; nếu provider thiếu hoặc trả lỗi thì **zone không kích hoạt Simulator** (fail closed), đồng thời log cảnh báo một lần.

Admin có thể khai báo nhiều zone trong `simulator.yml`; không phụ thuộc trực tiếp vào `allowed-regions` của EMF. **Điều kiện được phép câu vẫn phải thỏa policy gốc** `allowed-worlds`/`allowed-regions`/permission/rod/competition; zone Simulator chỉ quyết định **cách câu** chứ không ban quyền bảo vệ.

### 5.2. Quy tắc ưu tiên khi zone chồng nhau

- `disabled-zone` deny cao nhất (chặn Simulator, **không** tự động cho phép câu nếu EMF cũng cấm).
- Zone có `priority` lớn hơn được chọn; bằng priority: ID từ điển tăng dần để deterministic.
- Chỉ chọn zone khi **tọa độ phao đang trong vùng nước hợp lệ**; vị trí player có thể khác nhưng không vượt distance/session safety.
- Zone snapshot `{id, revision, world, bounds, policy}` chốt tại cast. Mỗi kỳ quan trọng (reserve/bite/commit) kiểm tra lại vị trí phao và quyền vùng; nếu rời hoặc config đổi thì cancel thay vì phát reward sai.
- Nếu không có zone match → route `VANILLA_EMF` (câu EMF cũ), không tự động chuyển thành native-only Minecraft.
- Nếu PacketEvents không sẵn sàng → `VANILLA_EMF` cho cast mới + warning admin; nếu admin chọn `on-unavailable: DENY`, block Simulator area theo chính sách rõ ràng, không lặng lẽ vô hiệu hóa toàn EMF.

### 5.3. `FishUtils.checkRegion()` cần được tái sử dụng cẩn thận

Source EMF hiện tại khi `allowed-regions` khác rỗng nhưng không có WorldGuard/RedProtect có thể **return true** sau khi warning. Trong Simulator phải có `ZoneResolver` policy **fail-closed** cho zone cần external provider, và phân biệt:

- `canUseEmfFishing`: legacy eligibility, do not expand permission.
- `isSimulatorZone`: capability + named zone membership.
- `canCatchAgentHere`: đánh giá ở hook location + agent water cell + player/rod/fish requirements.

### 5.4. Cache vùng

- Cache lookup world/cuboid theo **spatial cell** (ví dụ 16×16 block), invalidation theo config revision.
- WorldGuard query không được thực hiện cho **từng cá từng tick**; chỉ ở cast, kiểm tra lại trước commit, hoặc khi fish bị chuyển cell/zone.
- Với region thay đổi realtime do plugin khác, dùng TTL ngắn hoặc hook nếu có; phải revalidate quyền cuối cùng trước reward.
- Không assume WorldGuard query / `BukkitAdapter` thread-safe khi chạy async; gọi trên Paper owner thread.

---

## 6. Fish population và WaterIndex

### 6.1. Hai loại population

| Loại | Viewer thấy | Agent ownership | Khi bị catch | Use-case |
|---|---|---|---|---|
| `SHARED` | Mọi player gần cùng zone | Server zone-level | Despawn với mọi viewer; một session được claim | Đàn cá thường, không gian hồ thật |
| `PRIVATE` | Chỉ player có UUID được cấp | Player + zone | Despawn riêng player; player khác không thể target | Cá đặc biệt/rare event, riêng người chơi |

**Khuyến nghị:** phần lớn agent là `SHARED`; một phần cá hiếm được spawn `PRIVATE` theo rarity/zone/condition, có giới hạn chặt để không tăng tuyến tính vô hạn theo tổng player online.

### 6.2. WaterIndexService

- Chỉ lấy điểm nước từ **chunk đã load**; tuyệt đối không force-load/generate chunk để tìm nước.
- Mỗi entry: `{worldKey, chunkX, chunkZ, waterSurfaceY, candidateDepth, waterConnectedness, obstructionMask?, lastScannedTick, configRevision}`.
- Scan theo queue có `blocks-per-tick` và `max-scan-nanos` riêng; có `max-chunks-per-batch`.
- Tránh nước một block, khu vực sát lava/solid wall, và spawn agent trong block air.
- Có điều kiện `minimum-depth`, `minimum-volume` và `prefer-open-water`; không bắt buộc phải đúng vanilla `FishHook.isInOpenWater()` nếu admin muốn hồ nhỏ, nhưng không cấp **vanilla treasure** trái quy tắc open-water khi sử dụng vanilla loot bridge.
- Chunk load/unload, block water update, zone reload → invalidate index; queue refresh bounded/debounced, không quét toàn world mỗi tick.
- Tính toán tuyến đường trên dữ liệu water grid snapshot có thể async; **đọc block/world để chụp snapshot phải đúng world/region thread**.

### 6.3. Spawn algorithm

1. Xác định zone active + những viewer đang gần mặt nước.
2. Lấy tập candidate water cell hợp lệ từ index đã cache.
3. Kiểm tra density cap theo cell/zone/world/global; ưu tiên spawn nơi viewer có khả năng nhìn thấy.
4. Dùng fish catalog EMF để tạo `FishKey` theo rarity/weight và zone policy. Không gọi `Fish.init()` khi spawn; `Fish.init()` hiện bao gồm effects nên chỉ dùng ở commit sau khi refactor side-effects.
5. Gán renderer mặc định (`COD`, `SALMON`, `TROPICAL_FISH`, `PUFFERFISH`) theo map fish/rarity; nếu resource pack custom model enabled thì chọn `ITEM_DISPLAY` metadata tương ứng.
6. Spawn packet chỉ tới viewer hợp lệ và trong range; đăng ký packet entity ID/viewer state.
7. Agent bơi tới waypoint trong water grid; đổi hướng theo interval và tránh đi xuyên block.
8. Khi hết TTL/no viewers/chunk unload/zone shutdown, despawn + gửi destroy packet một lần cho từng viewer từng thấy.

### 6.4. Movement/AI states

`SPAWNING → WANDERING → INTERESTED → APPROACHING → BITING → (CAUGHT | FLEEING | WANDERING) → DESPAWNED`.

- `WANDERING`: điểm đến ngẫu nhiên hợp lệ, path ngắn; không dùng native mob AI.
- `INTERESTED`: agent được chọn cho hook nhưng **chưa bị reservation**; có thể lựa chọn lại nếu trong vùng xa.
- `APPROACHING`: reservation atomic đã thành công; path đến phao, có khoảng cách đến bề mặt và waypoint kiểm tra nước.
- `BITING`: cập nhật chuyển động phao + cue; không chuyển sang cá khác.
- `FLEEING`: nếu hụt, agent thoát reservation, có cooldown per agent để tránh cùng một con cắn liên tục.
- `CAUGHT`: đánh dấu consumed trước khi xử lý side effects; mọi viewer nhận despawn.
- `DESPAWNED`: terminal. Late packet callbacks không được hồi sinh agent cũ.

### 6.5. Ownership và reservation

- Index `agentId → FishAgent`, `sessionId → reservedAgentId`, `viewerUUID → visibleIds`.
- Reservation thực hiện theo single owner task (Paper main/world scheduling, **không hai thread cùng ghi**); nếu cần concurrent map thì dùng CAS/version đúng nghĩa.
- Chỉ một session có thể reserve `SHARED` agent. Khi catch: claim token rồi set `consumed` trước khi phát reward; mọi session trùng token đều fail.
- `PRIVATE` agent bị khóa theo UUID ngay khi sinh ra, không thể target bởi người khác dù packet spoof hoặc client mod.
- Mỗi reservation có TTL, generation, owner; cleanup khi timeout, player quit, hook invalid, teleport, zone revision đổi.
- Không dùng packet client (`UseEntity`, fake entity interaction) để quyết định thắng/thưởng.

### 6.6. Không có cá thì sao?

- Lúc cast nếu zone chưa ready (index chưa xong, PacketEvents unavailable) → **chọn legacy mode ngay** nếu policy cho phép, không bắt player đứng chờ vô hạn.
- Nếu đã nhận Simulator session nhưng tạm hết cá → bounded wait / spawn-on-demand trong budget; hết timeout thì message "chưa có cá" và cleanup, **không chuyển sang legacy mid-cast** vì rất dễ double loot.
- Nếu profiler thấy over-budget → giảm spawn/render/detail, **không** tắt reward validation hoặc tự thưởng cá.

---

## 7. Fish selection — tôn trọng EMF nhưng không tráo cá

### 7.1. Vấn đề cơ bản cần giải quyết

EMF cũ roll fish theo **người chơi ở thời điểm kéo**. Simulator thì **cá đã xuất hiện trước và có danh tính cố định**. Do đó không thể giữ **chính xác** mọi phân phối RNG cũ nếu chỉ bắt ngẫu nhiên cá thấy trong hồ. Phải có chính sách hai tầng:

1. **Population sampling:** tạo fish IDs của quần thể theo rarity/weight/catch-type/water zone.
2. **Attraction sampling:** quyết định agent nào **được phép** và **có xác suất** bơi tới phao của một player, dựa trên bait/rod/luck/requirements.

Đối với cá hiếm riêng, `PRIVATE` spawn có thể được điều chỉnh theo player/rod/bait nhưng phải giữ identity ngay từ khi người chơi bắt đầu nhìn thấy nó.

### 7.2. Eligibility matrix tại thời điểm tìm target

| Điều kiện EMF | Shared agent | Private agent | Rule |
|---|---|---|---|
| `Fish.getCatchType()` | Bắt buộc `CATCH/BOTH` | Tương tự | Không spawn fish chỉ để `HUNT` như catch target |
| Rarity/fish requirements | Recheck theo player + hook position | Filter ngay khi spawn và recheck | World, biome, Y, permission, time, addon condition |
| Custom rod allowed rarity/fish | Recheck | Recheck | Không hút cá không được phép |
| Bait boosts | Attraction weight | Spawn/attraction weight | Không đổi danh tính cá đã hiển thị |
| EMF `region-boosts` | Weight attraction / zone abundance | Spawn weight | Hỗ trợ multiplier số thực, bao gồm `< 1` |
| Competition allow fishing/worlds | Recheck | Recheck | Bảo vệ competition policy |
| Player fishing toggle | Stop targets/rewards | Despawn/expire | Không target khi tắt custom fishing |
| `require-custom-rod` / rod permission | Recheck | Recheck | Dùng rule hiện tại |
| `only-in-competition` | Recheck | Recheck | Không bypass khi Simulator bật |

**RequirementContext** phải mang `hookLocation`, `world`, `player` và kiểu câu tương đương nước vanilla, không dùng vị trí player làm mặc định nếu phao đang nằm ở biome/region khác.

### 7.3. Mô hình trọng số gợi ý

Đối với candidate agent `a` trong tầm hook của player `p`:

`effectiveWeight(a,p) = baseAttraction(a) × rarityWeight(a) × fishWeight(a) × rodMultiplier(a,p) × baitMultiplier(a,p) × competitionBuff(a,p) × regionMultiplier(a.zone)`

- **Chỉ áp dụng 1 lần mỗi modifier theo thiết kế**. Nếu `population` đã phân phối theo rarity, không mặc nhiên nhân lại rarity weight một lần nữa dẫn tới bình phương xác suất; cần chọn `target-distribution: population-driven | normalized-to-emf` và test phân phối.
- Mọi multiplier finite, không âm, clamp tại range admin-config; `0` tức bị loại, không ép về `1`.
- `bait.boost: -1` hoặc các `WeightModifier` đặc biệt của EMF phải có adapter riêng; không giả định mọi modifier đều phép nhân thông thường.
- `preDecidedRarities` của API cần được honor ở session start bằng target-preference/private agent nếu hợp lệ; không drop âm thầm.
- RNG ổn định cho test (injectable Random source) và không cho player cung cấp seed.

### 7.4. Điều kiện phân phối công bằng

- `SHARED`: hai người nhìn cùng một con; người có rod/bait mạnh hơn **có thể thu hút nó dễ hơn**, nhưng không được nhận cá khác với danh tính con đó.
- `PRIVATE`: chỉ xuất hiện ở viewer được cấp, target/rarity được roll theo quyền của họ; cần quota, cooldown, tránh spam cast để reroll hàng loạt.
- Nếu yêu cầu "rarity odds y hệt vanilla EMF" là tuyệt đối trong tương lai, cần một mode riêng `player-tailored-population` khiến nhiều cá private hơn. **Mặc định Q2=C ưu tiên độ sống động/chia sẻ hơn parity 100%.**
- Ghi `catch_distribution_by_rarity`, `bait_effectiveness`, `target_failure_reason`, có simulation test ≥100k lựa chọn per scenario để so với mức mong đợi.

---

## 8. Rod, bait, buff, competition — giữ gameplay gốc

### 8.1. Rod snapshot và anti-swap

Tại `FISHING` lưu:

- `EquipmentSlot` do event cung cấp khi có; UUID chủ cần/cast hand.
- Fingerprint an toàn: material, EMF rod ID, enchants, metadata/PDC quan trọng, bait revision/charges; **không dựa item display name**.
- Rod stats snapshot: `luck-modifier`, `lure-modifier`, `rarity-multipliers`, `allowed-rarities`, `allowed-fish`, `double-catch-chance`, `exp-multiplier`.

Tại `BITE_READY` và `COMMITTING`, xác minh người chơi vẫn giữ đúng rod/hợp lệ, không đổi sang cần có bonus cao hơn. Policy mặc định: nếu thay cần trong khi đang câu Simulator → **cancel session không thưởng**. Đối với vanilla fallback, sửa rod-hand tracking độc lập.

### 8.2. Ý nghĩa các rod stat trong Simulator

| Stat EMF | Simulator |
|---|---|
| `lure-modifier` | Rút ngắn thời gian search/interest, không ép Paper vanilla bait tự động |
| `luck-modifier` | Điều chỉnh eligible attraction/species distribution theo existing EMF logic |
| `rarity-multipliers` | Tăng/giảm weight agent tương ứng, tôn trọng quota |
| `allowed-rarities`, `allowed-fish` | Hard filters lúc search, reserve và commit |
| `double-catch-chance` | Một lần bắt thành công có thể cho x2 **quantity**, không tạo 2 lần catch event |
| `exp-multiplier` | Áp dụng cho XP thuộc EMF/session, không nhân thêm một lần vào vanilla EXP song song |
| `allow-baits` | Nếu false, bait modifier/consume đều bỏ qua; nếu true phải thực sự áp dụng |

### 8.3. Bait behavior

- Bait ảnh hưởng **attraction/rarity eligibility** trước khi một cá được reserve; không dùng để tráo loại cá sau `BITE_READY`.
- Phối hợp **custom rod + bait** theo policy rõ ràng: `allowedFish`/`allowedRarities` là hard filters; rarity/fish modifiers cộng dồn theo rule configured (recommended multiplicative normalized) và cap an toàn.
- Snapshot loại bait và charges khi cast; **không tiêu hao ở lúc chọn cá**.
- Với lần câu thành công **và bait thực sự có modifier ảnh hưởng lựa chọn**, consume đúng **1 charge** tại commit; hụt/cancel/timeout thì không mất bait. Bait vô hạn (`infinite`) vẫn hoạt động như cũ.
- Không cho thay rod/bait giữa cast và reel để copy charge/bonus; khi charge/fingerprint lệch → fail state, không thưởng.
- `bait.competition-disable: true` phải được tôn trọng ở cả population attraction và consume, không chỉ sau catch.
- Alert `BAIT_USED` chỉ gửi một lần **sau khi** charge đã được tiêu hao thành công.

### 8.4. `bait.catch-percentage` và loot không phải cá

EMF hiện cho phép câu lên **một bait item** thay vì cá thông qua `getBaitItem()`. Để không vi phạm "thấy cá A nhưng nhận bait B", Simulator cần phân loại mục tiêu:

- `FISH_AGENT`: biểu diễn một fish thật từ catalog EMF.
- `BAIT_LOOT_AGENT` (private visual nhỏ, bọt/lure bundle/ItemDisplay): xác định **trước** khi viewer thấy hoặc khi bắt đầu approach; khi kéo lên nhận bait item, **không phát `EMFFishCaughtEvent`**.
- Config `simulator.loot.bait-catch.enabled` và chance đồng bộ `bait.catch-percentage`, không roll bất ngờ ở cuối khi target là cá.

Nếu phần bait loot visual chưa hoàn thành thì **không bật rollout full compatibility** cho Simulator ở server đang bật bait drop; không lặng lẽ bỏ tính năng.

### 8.5. Competition/stat consistency

- Cả `VANILLA_EMF` và `SIMULATOR` đi qua **một** post-catch dispatcher; không cho competition tính hai lần.
- Giữ cách tính size/rarity/length của EMF, đừng sinh kích thước khi agent spawn; length được xác định khi commit (trừ khi admin muốn display exact size có thiết kế bổ sung).
- Theo behavior EMF hiện hành: một catch với `double-catch` vẫn là **một catch event**, có thể nhận `2` item. Tách metrics `catchAttempts`, `successfulCatches`, `itemsGranted`; tài liệu rõ stats đếm lượt hay số item.
- Competition `required-worlds`, `allow-fishing`, buffs, `catch-only-in-competition` áp dụng theo vị trí phao như quy tắc hợp lệ; cạnh tranh `SHARED` một cá → chỉ một player đạt điểm.
- Player đang trong competition nhưng ra ngoài Simulator zone vẫn chơi EMF vanilla như cũ.

---

## 9. `PlayerFishEvent` và Native FishHook Bridge — phần khó nhất

### 9.1. Nguyên tắc tích hợp Paper

- Luôn kiểm tra `event.isCancelled()` ở event router; không start Simulator khi plugin bảo vệ đã hủy cast.
- `FISHING`: phân quyền engine, capture hook ID, hand/rod, zone snapshot. Không hủy bừa event cast khiến phao không xuất hiện.
- Các state `BITE`, `FAILED_ATTEMPT`, `CAUGHT_FISH`, `REEL_IN`, `CAUGHT_ENTITY`, `IN_GROUND` phải được xử lý **theo session ownership**. Không thưởng ở `BITE`.
- Không dùng `FishHook#setBiteChance(0)` như phương pháp ngăn vanilla: Javadocs Paper 1.21.4 đánh dấu deprecated và ghi rõ **không có hiệu lực trên các phiên bản Minecraft mới**.
- Sử dụng Paper `FishHook` wait/bite time APIs **chỉ sau khi runtime spike xác minh**, ví dụ kiểm soát `setWaitTime(...)`/`setTimeUntilBite(...)` để tránh vanilla tự tạo item trước khi custom reel; không hardcode giả định 100% mọi server version.

### 9.2. Spike bắt buộc đầu Phase 1: native suppression

Thử hai hướng trên Paper 1.21.4 và 1.21.11 (hoặc bản target cuối):

**Strategy A — Control native wait:** Khi Simulator cast, đẩy chu kỳ native bite ra khỏi tuổi thọ tối đa của session; chỉ visual fake bite dựa vào state manager. Khi player kéo, nhận `REEL_IN`, tự commit EMF; không cho native `CAUGHT_FISH` xuất hiện.

**Strategy B — Guard native state:** Dùng callback listener `HIGHEST` để chặn `CAUGHT_FISH` vanilla trong session Simulator và xử lý mọi Item/XP phát sinh đúng policy; giữ independent state machine.

Khuyến nghị: **A là primary, B là safety guard**. Không coi strategy nào là proven trước khi đã chạy integration test theo từng bản Paper và với lure enchantment/mcMMO/AuraSkills.

### 9.3. Event routing table

| Event state | VANILLA_EMF | SIMULATOR |
|---|---|---|
| `FISHING` | Legacy apply lure/hologram | Kiểm tra zone, open session, suppress native fish bite |
| `BITE` | Legacy hook cue | Native unexpected bite: audit + guard; **không** tự chọn cá hoặc award |
| `REEL_IN` | Legacy cleanup | Nếu `BITE_READY` trong window → attempt commit; ngược lại fail/cleanup |
| `FAILED_ATTEMPT` | Legacy missed feedback | Fail/cleanup hoặc bounded reset, tuyệt đối không award |
| `CAUGHT_FISH` | Legacy EMF replacement | Unexpected native catch: prevent duplicate; remove/neutralize vanilla loot and XP theo verified Paper behavior |
| `CAUGHT_ENTITY` | Legacy behavior | Cancel session; giữ hành vi entity hooking vanilla đúng protection rule |
| `IN_GROUND` | Legacy cleanup | Cancel session, no reward |

Event `REEL_IN` / state thay đổi có thể khác theo version và loại hook; **thứ tự thực tế phải được ghi nhận bằng test**, không rely vào event timing tưởng tượng.

### 9.4. Ordering với plugin khác

- Validate cancellation lúc `HIGHEST`; việc plugin khác hủy sự kiện ở cùng hoặc priority sau cần được kiểm thử. Không thay thế hệ thống protection bằng `MONITOR` mutating listener.
- Với callback async/delayed, luôn revalidate zone, hook, player/rod state trước commit trên owner thread.
- Không tự tạo hoặc tự bắn lại `PlayerFishEvent` để kích hoạt addon bên ngoài; phát **EMF custom event** hiện tại đúng lúc.
- Nếu plugin khác sửa vanilla loot ở `CAUGHT_FISH`, Simulator phải có **explicit ownership policy**, không để kết quả phụ thuộc thứ tự listener ngẫu nhiên.
- Thêm integration matrix WorldGuard, RedProtect, mcMMO, AuraSkills, ItemsAdder/Oraxen/Nexo nếu server có; `cancelled` event không được tạo session/cá/reward.

### 9.5. Chest/treasure/native loot compatibility

EMF có `fishing.only-fish` và cấu hình tích hợp loot của mcMMO/AuraSkills. Không được lặng lẽ bỏ enchanted books/treasure/junk trong Simulator. Thiết kế thêm `CatchCategory`:

- `EMF_FISH`: fish ID từ catalog, phát `EMFFishCaughtEvent`.
- `BAIT_LOOT`: bait item từ EMF; không bị tráo thành cá.
- `VANILLA_TREASURE/JUNK`: dùng **loot-table compatibility adapter** (điều kiện open water, enchantment/biome/hook) và giữ item gốc; không trigger fish caught.
- Khi không có adapter tương đương trên target Paper và server yêu cầu `only-fish: true`/native treasure, **đưa zone vào mode `LEGACY_UNTIL_SUPPORTED` khi cast**, thông báo admin. Không tự giả lập tỷ lệ treasure mà không được phê duyệt.

**Acceptance gate:** Test `only-fish: true/false`, bait catch, non-fish treasure, mcMMO/AuraSkills hooks, inventory full, double catch; mỗi case chỉ có đúng một kết quả.

---

## 10. Catch/reward/DB transaction policy

### 10.1. Pipeline chuẩn (thiết kế)

```text
BITE_READY + valid REEL_IN
    ↓
ATOMIC_CLAIM_SESSION            (only one winner)
    ↓
REVALIDATE                     (hook, owner, rod, bait, zone, fish requirements, protection)
    ↓
PREPARE_FISH_CLONE             (from FishKey; size, lore, item snapshot)
    ↓
EMFFishCaughtEvent             (cancellable, exactly once)
    ├── CANCELLED ───────────► NO REWARD + RELEASE/CLEANUP
    ↓
DELIVERY_CAPACITY_POLICY       (inventory or safe drop, planned before destructive actions)
    ↓
COMMIT_CONSUMPTION_AND_LOOT    (bait charge + one fish item/quantity; compensate if partial fail)
    ↓
POST_COMMIT                    (EMF rewards, message, broadcast, competition, metrics, DB queue)
    ↓
CATCH_VISUAL + CLEANUP
```

**Quan trọng:** Source hiện tại `Fish.init()` gọi `generateSize()`, `checkMessage()`, `checkEffects()` và effects có thể lên lịch trước EMF event được chấp thuận. Refactor `prepare fish data` khác `apply effects` để event bị hủy **không để lại potion/effect/reward**.

### 10.2. Exactly-once trong phạm vi một phiên

- Một `sessionId` UUID, `completionToken` và atomic state transition `BITE_READY → COMMITTING`.
- Reservation `FishAgent` được claim một lần; duplicate event, spam reel, same-tick races hoặc callback muộn không thể trả cá thứ hai.
- Commit chạy trên owner thread; mọi async DB operation chỉ nhận immutable record, không gọi Bukkit API.
- Callback async phải kiểm tra `sessionGeneration`; nếu player cast phiên mới thì callback cũ bị bỏ.
- **Không tuyên bố distributed exactly-once** với commands/economy provider bên ngoài khi server crash; chỉ đảm bảo at-most-once per active session và đưa optional ledger cho reward có tính giao dịch cần durable semantics.

### 10.3. Delivery policy

- `give-straight-to-inventory: true` → nếu đủ chỗ, add item; nếu đầy, dùng fallback drop an toàn ở vị trí player/hook theo config; không xóa item mới chỉ vì inventory full.
- Nếu tự thả item, chỉ spawn ở world được load, vị trí hợp lệ, không vào protected void; có pickup-owner protection tùy cấu hình.
- `double-catch` cần split stack theo max stack size, bảo đảm tổng quantity không overflow/âm và đúng kết quả.
- Nếu tiền thưởng/command reward fail một phần, ghi log/audit và retry policy có key; **không** chạy lại toàn catch vì sẽ nhân đôi cá.
- Không cho `ItemStack` vừa trao bị chỉnh sửa qua reference dùng chung; clone item trước khi move cho inventory/dropped entity.

### 10.4. Event/API + stats ordering

- Ghi stats **sau khi catch thực sự chấp thuận và delivery thành công**; không chỉ dựa vào việc một cancellable pre-event đã được gọi.
- `EMFFishListener` hiện bắt `EMFFishCaughtEvent` mà không check cancelled; kiến nghị di chuyển internal DB aggregation sang post-commit dispatcher, và giữ compatibility listener read-only (nếu cần).
- `Leaderboard.applyToLeaderboard()` chỉ sau accepted catch; không tính khi event bị hủy/bait-loot/treasure/hụt.
- Report một lần cho `one catch`, `quantity` là field riêng; giữ nghĩa legacy của stats cho dữ liệu đã tồn tại.
- `FishLog` có timestamp, userUUID, fish key, length, competition ID và `catchSessionId` tùy chọn trong **new audit metadata**, không sửa/corrupt bảng cũ nếu không cần.

### 10.5. Database refactor cần làm song song

- Không query `UserManager.getUserId`, `loadFishStats`, `loadUserFishStats`, `getUserReport` từ callback main-thread của catch/join.
- Init async khi join, cache theo UUID; khi chưa sẵn sàng phải có chính sách `NO_RECORD_UNTIL_READY`/durable queue, **không** ghi đè dữ liệu thật bằng profile trống.
- `DataManager.flushDirty`: version/generation per key, chỉ clear dirty nếu revision ghi xong đúng revision hiện tại; retry có backoff nếu DB lỗi.
- Bounded queue, batch writes, metrics queue depth/dropped records; timeout flush khi disable.
- Simulator fish agents/session state là ephemeral **không cần thêm bảng DB riêng**; dữ liệu cá người chơi/competition vẫn theo DB EMF.

---

## 11. Packet renderer, resource pack, HUD và animation

### 11.1. Renderer backend

- **Backend mặc định:** `PacketEvents` 2.x dưới một `PacketTransport` adapter cô lập; không dùng trực tiếp NMS hoặc reflection cho fish render ở service layer.
- **Không có entity server** cho cá packet: client nhận virtual `SPAWN_ENTITY`, metadata, movement, destroy; server lưu vị trí/identity thật trong `FishAgent`.
- Packet entity IDs cấp phát từ allocator riêng, quản lý theo viewer/session, có kiểm tra trùng và hạn chế reuse ngay sau destroy.
- Wrapper và entity metadata phải test theo Paper/Minecraft phiên bản đích; packet protocol không được giả định bất biến giữa minor versions.
- Nếu PacketEvents không có hoặc version incompatible: disable **SIMULATOR feature**, giữ EMF legacy. Không class-load trực tiếp PacketEvents types trong bootstrap/core trước capability check.
- `PacketEvents` nên là runtime dependency bên ngoài (`softdepend` + capability check) nếu chọn failover; **không shade** PacketEvents core để tránh trùng manager/protocol handler.

### 11.2. Vanilla fish model mặc định

| Fish family / fallback | Entity visual | Mô tả |
|---|---|---|
| Thường, không có mapping | Virtual `COD` | Cơ chế chung, không cần texture pack |
| Nhóm salmon | Virtual `SALMON` | Tùy `fishKey`/rarity mapping |
| Nhóm tropical | Virtual `TROPICAL_FISH` | Màu/variant metadata nếu PacketEvents phiên bản đó hỗ trợ |
| Nhóm puffer | Virtual `PUFFERFISH` | Tùy layout và hitbox ảo của renderer |
| Không thể render | Fallback virtual `COD` | Warn debug một lần; **không** bỏ reward của fish |

Ngoại hình này là **đại diện** cho nhiều EMF fish, không có nghĩa mỗi custom fish có mob model độc lập. Những định nghĩa trong `rarities/*.yml` vẫn nhận đúng `ItemStack` EMF gốc.

### 11.3. Custom resource pack model (optional)

- Cho phép map `fishKey/rarity → ItemDisplay item_model/custom_model_data` hoặc cấu hình visual preset; không sửa fish item đang lưu trong inventory.
- `ITEM_DISPLAY` cũng được spawn bằng packet; `ItemStack` visual được tạo từ config đã validate và cache immutable templates.
- Có transform `{scale, rotation, translation, facing, glow, interpolation, animation-preset}` trong file `simulator/visuals.yml`.
- Với các client chưa chấp nhận pack: gửi fallback vanilla fish entity thay vì ItemDisplay hiển thị texture thiếu. Lấy trạng thái pack từ Paper events/capability nếu khả dụng, không tự đoán từ tên plugin.
- Tùy chọn PAPI/ItemsAdder/Oraxen/Nexo item model qua adapter, nhưng không bắt các hook này là hard dependency.
- Không gửi model ID, tên item, rarity private của người chơi A trong packets của người chơi B.

### 11.4. Đối tượng render khác

- `FishHook` là entity thật của Paper: visual cá chỉ **tiếp cận phao**, không tự spawn fake bobber khác.
- Hook waiting HUD: packet TextDisplay hoặc actionbar; tối đa 2–5 lần update/giây trừ khi cue thay đổi; không phát title mỗi tick.
- Bite cue: splash particle (nearby viewers nếu shared), bubble trail, note/sound, momentary glow/color, `REEL NOW` actionbar/title preset.
- Catch animation: cá nhảy ra mặt nước → chuyển động về người câu → despawn; **reward server được quyết định ngay ở commit**, cinematic không được tự grant reward lần thứ hai.
- Particle/sound visibility có tùy chọn `PLAYER_ONLY`, `NEARBY`, `NONE` để tránh spam cả hồ.
- Player preference opt-out heavy effects; command/GUI cũ không bị ép thêm menu.

### 11.5. Cấu hình hiệu ứng theo rarity

| Preset | Waiting | Bite | Success | Failure |
|---|---|---|---|---|
| `common` | Ripple nhỏ | Splash + click | Cá kéo nhanh, 0.5–0.8s | Cá bơi thoát |
| `rare` | Bubble trail | Glow + note | Spiral nhẹ + sound | Smoke bubble |
| `epic` | Color trail | Pulse + sound riêng | Jump + burst | Fade-out |
| `legendary` | Ánh sáng tinh tế | Cue đặc biệt **không che tầm nhìn** | Cinematic ngắn 1–2s + broadcast EMF | Flee effect |

Không bật animation dài gây chiếm điều khiển camera/freeze player làm mặc định. Admin chỉnh rarity mapping và mỗi `fishKey` override.

### 11.6. Relevance + anti-cheat

- Player không tấn công, đặt lên, teleport, hoặc hook fake fish entity như entity thật; chúng không ở Bukkit world.
- Không dùng client click packet fake fish để cấp item; chỉ canonical `PlayerFishEvent` + server validation.
- Nếu các anti-cheat block packet fish metadata hoặc client lỗi, toggle `visuals.ambient-enabled` có thể tắt nhưng **không** để state/reward mâu thuẫn. Runtime policy có thể tự fallback legacy cho lượt câu mới.

---

## 12. Configuration schema — cấu hình rõ, có comment và validation

### 12.1. Chiến lược file

| File | Vai trò |
|---|---|
| `config.yml` hiện có | **Giữ nguyên** các key và migration `version: 8` hiện tại; không tự ý overwrite |
| `simulator.yml` **mới** | Feature toggle, world/region zones, water sampling, fish AI, budgets, integration, fallback, gameplay |
| `simulator/visuals.yml` **mới** | Renderer type/model mapping per rarity/fish, resource pack fallback, entity display transform |
| `simulator/effects.yml` **mới** | Bite/catch/missed sound/particle/HUD/cinematic presets |
| `messages.yml` / `locales/messages_*.yml` | Keys Simulator + permission/help/error; dùng hệ thống EMF hiện tại và fallback |
| `rarities/*.yml` | **Không bắt buộc sửa**; có thể thêm block `simulator:` optional per rarity/fish nếu admin muốn |
| `rods/*.yml`, `baits/*.yml` | Giữ schema hiện có; adapter đọc trực tiếp modifiers; optional extra behavior override |

### 12.2. Phác thảo `simulator.yml` (schema đề xuất, **chưa phải config đã được plugin hỗ trợ**)

```yaml
config-version: 1
simulator:
  enabled: false  # Mặc định off cho đến khi hoàn tất integration test
  mode: REGIONS_ONLY
  dependency:
    packet-events-required-for-simulator: true
    on-missing-packets: LEGACY_EMF  # LEGACY_EMF | DENY
  policy:
    permission-required: false
    # Rule EMF cũ vẫn áp dụng: allowed-worlds/regions, custom-rod, competition...
    zone-based-on: HOOK_LOCATION
    on-region-provider-missing: LEGACY_EMF
    on-zone-changed-mid-session: CANCEL
    on-config-reload: CANCEL_AND_CLEANUP
  gameplay:
    bite-window-ticks: 26
    grace-ticks: 3
    target-search-min-ticks: 35
    target-search-max-ticks: 150
    session-timeout-ticks: 600
    max-retarget-attempts: 2
    max-reel-distance: 35.0
    early-reel: FAIL
    missed-bite: FAIL
    bait-consume-on: SUCCESS_WITH_EFFECTIVE_MODIFIER
    rod-swap: CANCEL
    auto-reel: false
  water-index:
    only-loaded-chunks: true
    max-blocks-per-tick: 512
    max-chunks-per-batch: 2
    scan-budget-ms: 0.8
    minimum-depth: 2
    minimum-water-volume: 8
    invalidate-on-block-change: true
    refresh-debounce-ticks: 20
  population:
    global-fish-cap: 240
    per-zone-fish-cap: 72
    max-private-fish-per-player: 2
    global-private-cap: 64
    fish-agent-ttl-seconds: 180
    spawn-interval-ticks: 40
    simulation-interval-ticks: 2
    max-active-sessions: 100
    max-search-distance: 12.0
    private-rare-spawn-enabled: true
    private-rarities: [Epic, Legendary]
  movement:
    base-speed: 0.10
    turn-interval-ticks: 20
    max-path-nodes: 32
    max-targeting-ops-per-tick: 30
    escape-cooldown-ticks: 60
  packets:
    backend: PACKETEVENTS
    max-per-tick-global: 1200
    max-visible-fish-per-player: 20
    view-distance: 48
    movement-near-interval-ticks: 2
    movement-mid-interval-ticks: 5
    movement-far-interval-ticks: 12
    packet-queue-max-size: 8000
    adaptive-budget: true
    priority: [DESTROY, BITE, SPAWN, MOVEMENT, AMBIENT]
  loot:
    fish-source: EMF_CATALOG
    preserve-emf-event-and-rewards: true
    on-inventory-full: SAFE_DROP
    only-fish-compatibility: AUTO_LEGACY_UNTIL_SUPPORTED
    vanilla-treasure: COMPATIBILITY_BRIDGE
    bait-catch:
      enabled: true
      visual: BAIT_LOOT_AGENT
  effects:
    default-preset: common
    nearby-audience-radius: 24.0
    max-particles-per-bite: 30
    actionbar-update-interval-ticks: 5
  performance:
    disable-ambient-before-gameplay: true
    metric-sample-seconds: 30
    warn-on-packet-drop-rate: 0.10
    over-budget-policy: REDUCE_LOD
  debug:
    enabled: false
    sample-rate: 0.02

zones:
  # Ví dụ 1: toàn world chuyên câu cá
  lake_world:
    enabled: true
    type: WORLD
    world: fishing_world
    priority: 10
    population-cap: 72

  # Ví dụ 2: cuboid bên trong world Survival
  central_lake:
    enabled: true
    type: CUBOID
    world: world
    min: { x: -120, y: 55, z: 220 }
    max: { x: 120, y: 115, z: 460 }
    priority: 30
    population-cap: 55

  # Ví dụ 3: WorldGuard region đã tạo từ trước
  harbor_fishing:
    enabled: false
    type: WORLDGUARD_REGION
    world: world
    region-id: harbor_fishing
    priority: 50

  # Ví dụ 4: RedProtect (nếu provider tồn tại)
  rp_fishing:
    enabled: false
    type: REDPROTECT_REGION
    world: world
    region-id: fishing_pond
    priority: 20
```

**Chú ý:** Đây là **đặc tả cấu hình tương lai**, không phải tài liệu khẳng định những key này đã hoạt động trong ZIP hiện tại. Những con số là baseline an toàn để bắt đầu tune, không phải benchmark 100–200 players.

### 12.3. `simulator/visuals.yml` (ý tưởng cấu trúc)

```yaml
config-version: 1
visuals:
  require-resource-pack: false
  fallback-renderer: VANILLA_COD
  defaults:
    renderer: VANILLA_FISH
    model: COD
    scale: 0.8
    glow: false
  rarity-overrides:
    Rare: { model: SALMON }
    Epic: { model: TROPICAL_FISH, glow: true }
    Legendary: { model: PUFFERFISH, glow: true }
  fish-overrides:
    # Rarity ID + Fish name/ID phải resolve được qua FishManager.
    # "Legendary:Golden Koi":
    #   renderer: ITEM_DISPLAY
    #   item-model: "ledat:fish/golden_koi"
    #   scale: 0.65
```

- Khi định nghĩa override sai, log `fishKey`, disable **mapping lỗi**, dùng default model; không disable toàn plugin.
- `item-model`/custom-model-data resolution tùy target Paper/resource pack, cần validation theo phiên bản cụ thể.
- Cần thêm `max-viewer-custom-display`, `interpolation` và transform khi làm visuals phase thực tế.

### 12.4. Message keys yêu cầu

Tất cả user-facing texts (bao gồm help và event message) nằm ở `messages.yml` / locale EMF, dự kiến:

| Key | Mục đích |
|---|---|
| `simulator.cast-started` | Câu bắt đầu; hạn chế spam, disabled mặc định |
| `simulator.waiting` | Đang chờ cá |
| `simulator.target-interested` | Cá bắt đầu bơi tới |
| `simulator.bite-now` | **KÉO NGAY!** |
| `simulator.reel-too-early` | Kéo quá sớm |
| `simulator.bite-missed` | Hết reaction window |
| `simulator.fish-escaped` | Cá thoát |
| `simulator.no-fish` | Không có cá phù hợp quanh phao |
| `simulator.zone-disabled` | Region/world không cho Simulator |
| `simulator.rod-changed` | Đổi cần giữa lúc câu |
| `simulator.no-permission` | Không đủ quyền |
| `simulator.invalid-water` | Phao không ở vùng nước hợp lệ |
| `simulator.unavailable` | Backend Simulator unavailable; hiển thị theo policy |
| `simulator.admin.status`, `.zone-list`, `.reload-ok`, `.reload-error` | Diagnostics/admin |
| `simulator.admin.invalid-zone`, `.invalid-number`, `.player-only`, `.usage` | Input validation |

Message dùng Adventure/MiniMessage qua EMF `MessageConfig`/`EMFMessage`, placeholder `{fish}`, `{rarity}`, `{zone}`, `{time}`, `{distance}`, `{reason}`; escape input không tin cậy, tắt italic mặc định cho item lore nếu cần.

### 12.5. Config validation

- Validate `config-version`, enum modes, `zone.type`, `world` tồn tại, min ≤ max, priority range, region ID, material/entity type, sound, particle, model ID, finite numeric multipliers, speed > 0, 0 ≤ chance ≤ 1, range distance, size.
- Budget hard cap chống config sai tạo task/spawn vô hạn; `max-per-tick-global` > 0, queue bounded, private cap ≤ global cap, view distance không vô hạn.
- Kiểm tra `FishKey`/rarity tồn tại; cảnh báo missing resource-pack model và dùng fallback.
- `messages.yml` thiếu key thì fallback default locale hoặc message rõ ràng, log một lần/key.
- Migrate schema `simulator.yml` với backup, copy/supplement defaults không overwrite thiết kế user; giữ `config.yml` migration EMF legacy.

---

## 13. Command & Permission design

> Không yêu cầu thêm GUI quản lý riêng. Ưu tiên command admin ngắn + YAML; giữ `/emf` và command system đang dùng theo từng version-module.

| Command dự kiến | Sender | Permission | Chức năng |
|---|---|---|---|
| `/emf admin simulator status` | player/console | `emf.admin.simulator.status` | Health, engine readiness, fish counts, packets, queue |
| `/emf admin simulator zone list` | player/console | `emf.admin.simulator.zone` | Liệt kê zones + type/priority/enabled |
| `/emf admin simulator zone info <id>` | player/console | `emf.admin.simulator.zone` | Xem config/health zone |
| `/emf admin simulator here` | player-only | `emf.admin.simulator.inspect` | Tọa độ player/hook thuộc zone nào, eligibility reason |
| `/emf admin simulator reload` | player/console | `emf.admin.simulator.reload` | Validate, atomically swap config, cleanup sessions |
| `/emf admin simulator debug <player> [seconds]` | player/console | `emf.admin.simulator.debug` | Trace có TTL và rate limit |
| `/emf admin simulator off` | player/console | `emf.admin.simulator.manage` | Emergency disable cho cast mới, cleanup active |
| `/emf admin simulator on` | player/console | `emf.admin.simulator.manage` | Chỉ bật khi readiness đủ |
| `/emf admin simulator zone pos1/pos2/create ...` | player-only | `emf.admin.simulator.zone.edit` | **Phase optional** cuboid setup; không bắt buộc khi core release |

- Quyền sử dụng của player: nếu `permission-required: true` → `emf.simulator.use`; mặc định tiếp tục check quyền `emf.use_rod` theo EMF config.
- Mọi subcommand có help/usage/tab complete/permission/no-permission, player-only, invalid-world/zone, range validation và cooldown tương ứng.
- Không tạo command root mới khi `/emf admin` có sẵn; module `1-21` đang dùng Paper lifecycle/Brigadier, module `1-20` dùng CommandAPI riêng, nên command registration phải ở abstraction chung hoặc chỉ enable trong variant được hỗ trợ.
- Không log player-specific data/sensitive bait details quá mức ở debug.

---

## 14. Source file structure dự kiến

> Mẫu đường dẫn tương đối trong `even-more-fish-plugin/src/main/java/com/oheers/fish/`. Tên file là proposed work items, **chưa tồn tại** trong source gốc.

```text
com/oheers/fish/
├── EvenMoreFish.java                       [MODIFY: bootstrap lifecycle]
├── plugin/
│   ├── EventManager.java                   [MODIFY: single routing listener]
│   └── ConfigurationManager.java           [MODIFY: Simulator config lifecycle]
├── config/
│   ├── MainConfig.java                     [KEEP: legacy migration]
│   ├── MessageConfig.java                  [EXTEND: locale keys]
│   └── simulator/
│       ├── SimulatorConfig.java            [NEW: config snapshot]
│       ├── SimulatorConfigLoader.java      [NEW: load/migrate]
│       ├── SimulatorConfigValidator.java   [NEW: validation]
│       ├── SimulatorZoneDefinition.java    [NEW]
│       └── SimulatorVisualConfig.java      [NEW]
├── fishing/
│   ├── FishingProcessor.java               [REFACTOR: thin router]
│   ├── Processor.java                      [REFACTOR: shared catch services]
│   ├── FishingHookHolograms.java           [KEEP: legacy mode only]
│   ├── EMFFishListener.java                [FIX: committed-only stats]
│   ├── catchlogic/
│   │   ├── FishEligibilityService.java     [NEW: shared eligibility]
│   │   ├── FishCatalogAdapter.java         [NEW: use FishManager]
│   │   ├── CatchPreparationService.java    [NEW: clone/size/item]
│   │   ├── CatchCommitCoordinator.java     [NEW: event/reward gating]
│   │   ├── BaitConsumptionService.java     [NEW: consume-on-success]
│   │   └── FishDeliveryService.java        [NEW: inventory/drop]
│   └── simulator/
│       ├── SimulatorModule.java            [NEW: lifecycle]
│       ├── SimulatorFishingEngine.java     [NEW]
│       ├── FishingEventRouter.java         [NEW]
│       ├── EngineMode.java                 [NEW: NOT FishingType enum]
│       ├── engine/
│       │   ├── FishingSessionManager.java
│       │   ├── FishingSession.java
│       │   ├── FishingSessionState.java
│       │   ├── NativeHookBridge.java               [interface; runtime adapter in version module]
│       │   └── ReactionWindowService.java
│       ├── zones/
│       │   ├── SimulatorZoneResolver.java
│       │   ├── WorldGuardZoneAdapter.java
│       │   ├── RedProtectZoneAdapter.java
│       │   └── ZoneSnapshot.java
│       ├── water/
│       │   ├── WaterIndexService.java
│       │   ├── WaterCell.java
│       │   └── WaterSnapshot.java
│       ├── population/
│       │   ├── FishAgent.java
│       │   ├── FishPopulationService.java
│       │   ├── FishMovementController.java
│       │   ├── FishSpawnPolicy.java
│       │   └── FishTargetingService.java
│       ├── reservation/
│       │   ├── FishReservationService.java
│       │   └── ReservationToken.java
│       ├── visual/
│       │   ├── PacketFishRenderer.java
│       │   ├── PacketTransport.java         [interface]
│       │   ├── PacketEntityIdAllocator.java
│       │   ├── PacketVisibilityService.java
│       │   ├── PacketBudgetManager.java
│       │   ├── SimulatorHudService.java
│       │   └── CatchCinematicService.java
│       ├── diagnostics/
│       │   ├── SimulatorMetrics.java
│       │   └── SimulatorDoctor.java
│       └── lifecycle/
│           ├── SimulatorCleanupListener.java
│           └── SimulatorReloadCoordinator.java
├── baits/BaitHandler.java                   [MODIFY: separate evaluate/consume]
├── fishing/items/FishManager.java           [FIX: boosts/location weighting]
├── database/data/manager/DataManager.java   [FIX: dirty revision]
└── commands/...                             [EXTEND: simulator admin branch]

# New resources
src/main/resources/simulator.yml
src/main/resources/simulator/visuals.yml
src/main/resources/simulator/effects.yml
src/main/resources/locales/messages_*.yml        [EXTEND]

# API module only if an extension event is justified
../even-more-fish-api/src/main/java/com/oheers/fish/api/events/
  EMFSimulatorPreCatchEvent.java                 [OPTIONAL]
  EMFSimulatorPostCatchEvent.java                [OPTIONAL]

# Variant-specific files
../even-more-fish-plugin-1-21/src/main/java/com/oheers/fish/simulator/packet/
  PacketEventsTransport.java                    [NEW: 1.21-only adapter]
  PaperFishHookBridge.java                     [NEW: Paper 1.21 hook controls]
../even-more-fish-plugin-1-21/build.gradle.kts    [ADD PacketEvents dependency]
../gradle/libs.versions.toml                     [ADD version catalog]
../even-more-fish-plugin-1-21/...                [PLUGIN DESCRIPTOR / TEST]
```

### 14.1. Guard chống overengineering

- Không tạo một abstraction chỉ có một implementation nếu nó không giảm dependency hoặc không cô lập version/protocol.
- `NativeHookBridge` và `PacketTransport` **nên** là interfaces vì chúng làm giảm rủi ro Paper/PacketEvents version changes.
- `FishCatalogAdapter` chỉ điều phối existing EMF `FishManager`, không tạo một database/catalog riêng.
- Shared catch commit service có thể refactor dần, không phải chuyển tất cả legacy methods sang package mới cùng một lúc.
- Không tạo GUI handler framework mới: EMF đã có GUI/shop/journal vận hành độc lập.

---

## 15. Performance và thread safety — bắt buộc theo budget

### 15.1. Nguyên tắc performance

**Event-driven cho tương tác, batched tick cho animation.** Không tạo scheduler riêng cho từng fish, per-player viewer hoặc mỗi fishing session.

| Resource | Budget baseline | Khi vượt budget |
|---|---:|---|
| Tổng fish agent | 240 | Ngừng spawn mới; prune cá xa viewer |
| Fish per zone | 72 | Rebalance density và ưu tiên active pond |
| Shared fish per viewer | 20 | LOD/cull cá xa và ít quan trọng |
| Private fish per viewer | 2 | Chờ cooldown, không thêm agent |
| Global private agents | 64 | Không tạo cá riêng mới |
| Active sessions | 100 | Cast mới chọn fallback được định nghĩa từ đầu hoặc reject rõ |
| Outgoing packets/tick | 1.200 | Hàng đợi ưu tiên; coalesce movement; không drop destroy quan trọng |
| Water blocks scan/tick | 512 | Hoãn cell/chunk chưa quét |
| Water scan wall budget/tick | 0,8 ms | Dừng scanning cho tick đó |
| Target operations/tick | 30 | Defer search, giới hạn queue |
| Per-viewer visual range | 48 blocks | Không gửi cá ngoài range |
| Unused agent TTL | 180s | Despawn deterministic |

*Đây là chỉ tiêu cấu hình ban đầu*, không phải số đo; cần tune trên hồ và lượng player thực tế. Đặc biệt 1.200 packets/tick là **trần**, không phải traffic mặc định mong muốn.

### 15.2. Level of Detail (LOD)

- **Near** (0–16 blocks): movement updates đều (ví dụ 2 ticks), cue đầy đủ.
- **Mid** (16–32 blocks): movement updates thưa (5 ticks), metadata on-change.
- **Far** (32–48 blocks): updates rất thưa (12 ticks), không particle ambient liên tục.
- **Outside**: destroy/cull, viewer không có entity reference.
- Packet movement có dead-zone threshold, rotation threshold; nếu cá không đổi đáng kể thì không gửi.
- Spawn và destroy tạo burst: queue bounded với priority `DESTROY > BITE > SPAWN > MOVEMENT > AMBIENT`, fairness round-robin giữa viewers; giữ distinct packet-state per viewer.
- Tick heartbeat monitor detects bottleneck, giảm ambience trước, không cắt validation hoặc giảm reward correctness.

### 15.3. Thread model (Paper)

| Operation | Context cho phép |
|---|---|
| Đọc `PlayerFishEvent`, hook/player/inventory, reward item | Paper main thread / đúng owner context |
| Đọc block/chunk/water, WorldGuard region | Paper world context, không async tùy tiện |
| Spawn/remove entity thật (hook, dropped loot) | World/entity owner thread |
| Quản lý `FishAgent` shared/reservation/session | Single owner ticker hoặc explicit synchronization, không chia writable state lung tung |
| Tính đường trên immutable `WaterSnapshot` | Có thể async bounded worker |
| Database/file I/O, logs batch, snapshot persistence | Async executor bounded |
| Packet serialization và send | Qua `PacketTransport` được xác minh đúng contract thư viện; snapshot/caller không gọi Bukkit async |
| Update title/actionbar/sounds/player effects | Quay về player/entity owner context |

EMF hiện dùng `UniversalScheduler`. **Không suy ra Folia-safe** chỉ vì có scheduler; cần audit toàn bộ cross-region operations, fish shared ownership, cache, plugin world hooks và renderer trước khi đặt `folia-supported: true`.

### 15.4. Scheduler ownership/cleanup

- Task gắn plugin owner, cancel khi disable/reload.
- Một batch task/world hoặc manager tick, không `N players × M fish` tasks.
- Async worker có queue cap, tên task/diagnostic, có timeout.
- Callback sau khi plugin disabled, session invalid hoặc config revision đổi → bỏ ngay, không dùng `Player` object giữ trước đó.
- Optional `LeDatPlatform` bridge chỉ dành deployment riêng, qua `vn.ledat.platform.api.*` nếu tích hợp sau; plugin EMF mặc định **không harddepend** vì repo là multipurpose/public plugin. Không dùng `vn.ledat.platform.core.*`.

### 15.5. Measurements bắt buộc

- `agent.count{zone,visibility}`, `viewer.count`, `session.count{state}`, `reservation.failures`, `search.duration_ms`, `water.scan.ms`, `packet.sent/dropped/queued{type}`, `ticking.cost_ms`, `tick.backpressure`, `catch.success_rate`, `bait.consume_count`.
- Metrics aggregate 30–60 giây, không log per fish mỗi tick; debug sampling theo UUID/zone với TTL.
- Tạo benchmark report theo 1/10/30/50/100 concurrent anglers và 0/50/200 agents, đo TPS, MSPT p50/p95/p99, CPU, heap, bandwidth/pings, queue drops. **Acceptance threshold** nên khóa sau khi có baseline server; mục tiêu sơ bộ không tăng p95 MSPT hơn **~2 ms** so với legacy ở cùng kịch bản medium load, và zero unbounded queue/leak.

---

## 16. Lifecycle, reload, failure policy, rollback

### 16.1. Startup order (sau refactor)

1. EMF config/messages/hook systems như trước.
2. Load `FishManager` → `BaitManager` → `RodManager` như source hiện tại.
3. Load/validate simulator config snapshot và vùng; xác định PacketEvents capability.
4. Khởi tạo `SimulatorModule` (core state, water index, renderer, diagnostics) nếu enabled/available.
5. Register **một** router/cleanup listeners, startup ticker sau khi catalog + hooks ready.
6. Log rõ `Simulator=ENABLED|DISABLED|DEGRADED`, zone count, packet backend, feature flags.

Nếu catalog lỗi, disable Simulator, tiếp tục EMF legacy theo existing EMF failure policy; nếu PacketEvents thiếu chỉ disable Simulator, không vô hiệu hóa sell GUI/competitions.

### 16.2. Reload order

1. Parse cấu hình mới vào **candidate snapshot**, migrate/validate mà không thay state đang chạy.
2. Nếu invalid → log key lỗi, giữ snapshot cũ, báo `reload-failed`; không destroy server state.
3. Nếu valid → tạm ngừng tạo session mới, cleanup/cancel phiên Simulator đang active (vì fish IDs/zone membership có thể đã đổi), destroy packets.
4. Swap config/zone/fish catalog revision atomically, invalidate water/viewer caches; reconcile loaded cells.
5. Mở lại admissions; log số sessions bị hủy và zones mới.
6. Event `EMFPluginReloadEvent` của EMF vẫn phát như cũ, không trigger twice.

Không kéo trạng thái `COMMITTING` qua reload; phải chốt hoặc abort an toàn tại boundary trong cùng owner tick.

### 16.3. Disable và player lifecycle

- `PlayerQuitEvent`, respawn, teleport/world change, hook removed, inventory/rod change, timeout, region disabled, server shutdown → cancel session/clear reservation.
- Packet renderer gửi destroy đến viewer online khi có thể; khi disconnect vẫn clear server-side index, không giữ UUID state vô hạn.
- `onDisable()` stop new admissions → stop tickers → cleanup sessions/visuals/agents → flush EMF DB bounded → unregister hooks/listeners → close dependencies.
- Không gọi scheduler đã shutdown khi cleanup; cần `shutdown` idempotent, thử gọi 2 lần không throw.

### 16.4. Failure matrix

| Tình huống | Kết quả bắt buộc |
|---|---|
| Thiếu PacketEvents lúc enable | Simulator degraded/off, legacy EMF hoạt động |
| PacketEvents bị disable giữa runtime | Stop sessions, cleanup, không grant muộn; legacy cho cast mới |
| Không có WorldGuard cho zone WG | Zone inactive, warn một lần; không fail open |
| Invalid entity model / material | Fallback visual per-fish; không crash toàn Simulator |
| Invalid world/zone tọa độ | Disable zone lỗi, giữ zone hợp lệ; rõ config key |
| Server TPS sụt, packet queue đầy | Giảm LOD, delay spawn/ambient, vẫn bảo vệ reward/session |
| Chunk unload trong lúc cá bơi | Despawn hoặc pause bounded; session fail an toàn |
| Player rời game lúc `BITE_READY` | Cancel; không nhận thưởng offline |
| Player thay rod hoặc hết bait | Abort theo policy; không nhân đôi charges |
| DB lỗi hoặc đóng | Không block main; queue/retry bounded; ledger optional |
| Cancel EMF caught event | Không item, reward, potion effect, stats, competition |
| Inventory full | Safe drop theo config hoặc fail/abort, không mất item/dupe |
| Reload lúc nhiều người đang câu | Cleanup generation-safe và destroy packets |
| Competition bắt đầu/kết thúc giữa session | Revalidate lúc commit, policy rõ (mặc định current competition at commit) |

### 16.5. Emergency rollback

- Feature flag `simulator.enabled: false` + `/emf admin simulator off` có hiệu lực cho cast mới.
- Không thực hiện migration phá data item/DB; toàn bộ legacy fish identity/metadata vẫn là source of truth.
- Nếu phase chưa qua QA, release giữ Simulator mặc định `false`.
- Khi rollback giữa thời điểm vận hành, active simulator sessions bị hủy và cleanup, người chơi câu lại theo EMF cũ; không chuyển session đang bite sang legacy.

---

## 17. Phase-by-phase Implementation Backlog

> **Quy tắc chung:** Mỗi phase phải tạo PR/commit độc lập, test đúng gate trước khi chuyển. Không đánh dấu phase hoàn thành chỉ vì class tồn tại hoặc source nhìn có vẻ ổn; phải build được + có test. **Hiện tại chưa thực hiện phase nào trong tài liệu này.**

### Phase 0 — Baseline, test harness, contract freeze

**Mục tiêu:** Chốt trạng thái EMF trước khi sửa engine; tránh "refactor xong mất chức năng mà không biết".

**Tasks**

- [ ] Backup source, commit baseline, sao lưu `config.yml`, `messages.yml`, `rarities`, `rods`, `baits`, competition configs, SQLite/MySQL data test.
- [ ] Xác định release variant: Paper 1.21.4+ nhánh `even-more-fish-plugin-1-21`; xem khả năng build với current compileOnly 1.21.1 và bản target 1.21.11; không thay core Java 21 hoặc `26-1` Java25 tùy tiện.
- [ ] Xác minh build convention plugins (`org.evenmorefish.fish.*`) có sẵn trong môi trường/build checkout; ZIP nguồn được cung cấp có tham chiếu tới plugin convention, nên build gate phải kiểm tra resolution từ checkout hợp lệ.
- [ ] Chạy `Gradle test`/`shadowJar` baseline, ghi test pass/fail trước mọi thay đổi.
- [ ] Chụp snapshot loot/rarity/competition/bait/rod behavior để làm regression.
- [ ] Tạo thiết kế API & event ownership diagram, liệt kê plugin hooks cần test.
- [ ] Bổ sung tests cho `WeightedRandom`, bait+custom rod, region boost 0.5/1.8, cancelled event stats.
- [ ] Quyết định version compatibility matrix theo runtime thực, phân biệt full support với best-effort.

**Definition of Done / Gate G0**

- Build variant target hoạt động hoặc có blocker được ghi rõ + giải pháp; không tiến phase khi baseline chưa tái hiện được.
- Có golden test cho 10–20 fish samples/catch situations, list API/GUI/DB/competition behavior phải giữ.
- Không có thay đổi gameplay production.

### Phase 1 — Refactor legacy catch pipeline và fix P1/P2

**Mục tiêu:** Tách logic EMF cũ thành các service có thể được gọi bởi cả vanilla và Simulator mà **không đổi kết quả gameplay**.

**Tasks**

- [ ] Tách `Processor.getCaughtItem()` thành `Eligibility`, `Selection`, `Preparation`, `Commit` với legacy adapter.
- [ ] Tách `Fish.init()` data preparation khỏi side effects (effect/potion/reward) để cancellable event có nghĩa thật.
- [ ] Implement shared post-commit path: event, grants, messages, competition, metrics, stats theo ordering được kiểm thử.
- [ ] Sửa `CustomRod × Bait` theo `allow-baits`, tested coefficient logic, charge consume only on accepted fish catch.
- [ ] Sửa region boost số thực và lệch location ở `FishManager`, giữ compatible result với boost=1.
- [ ] Snapshot rod hand/fingerprint cho vanilla catch để tránh rod swap exploit.
- [ ] Sửa `EMFFishListener` cancelled stats và cold DB read, `DataManager.flushDirty` revision race.
- [ ] Regression các tính năng vanilla, hunt, baits/rods, shop/journal, custom fish API.

**Gate G1**

- `simulator.enabled: false`: compare trước/sau, fish distribution và reward/competition không regression (ngoại trừ bugfix được xác nhận).
- Event cancel không tạo item/stats/rewards/effects.
- Không có SQL query nặng sync trong join/catch path; dirty cache concurrent flush test pass.

### Phase 2 — Simulator config, zones, capability & event router

**Mục tiêu:** Bật routing theo world/region và có chế độ legacy fallback tuyệt đối ổn định, **chưa spawn cá packet**.

**Tasks**

- [ ] Thêm `SimulatorConfig`/validator/migration và candidate snapshot reload.
- [ ] Zone types `WORLD`, `CUBOID`, `WORLDGUARD_REGION`, `REDPROTECT_REGION`; priority/deny/revision + permission policy.
- [ ] `FishingEventRouter` chọn một engine tại `FISHING`, `SessionOwner` cố định theo hook UUID.
- [ ] Check hook world/water, player permission, rod, competition và effective zone.
- [ ] Capability probe PacketEvents không làm EMF crash khi vắng dependency.
- [ ] Admin `/emf admin simulator status`, `zone list/info`, `here` và localized messages.
- [ ] Khi không có zone hoặc feature disabled, route legacy hoàn toàn; tests region overlaps, provider missing và event cancellation.

**Gate G2**

- 100% cast ngoài zone đi vào legacy; không có catch listener thứ hai cùng trao thưởng.
- WorldGuard missing không mở Simulator toàn world.
- Reload invalid config giữ bản cũ, không leak listener/scheduler.

### Phase 3 — Native FishHook suppression proof + state machine skeleton

**Mục tiêu:** Chứng minh simulator có thể quản lý bite/reel mà không phát vanilla loot riêng.

**Tasks**

- [ ] Bổ sung runtime-specific `NativeHookBridge` cho Paper 1.21.x, isolate khỏi core source 1.20.1 API.
- [ ] Integration spike `FishHook#setWaitTime` hoặc timer native control và fallback unexpected `CAUGHT_FISH` guard.
- [ ] Log thực tế thứ tự event `FISHING`, `BITE`, `REEL_IN`, `FAILED_ATTEMPT`, `CAUGHT_FISH` trên Paper 1.21.4/1.21.11, normal/lure/enchanted rod/offhand.
- [ ] State skeleton `WAITING`, `BITE_READY`, `FAIL`, `CLEANUP` chưa award hoặc chỉ mock reward trên test.
- [ ] Bảo vệ event cancelled, hook invalid, player logout, rod change, rapid reel và cleanup generation.
- [ ] Test canonical reel timing và tương tác WorldGuard/mcMMO/AuraSkills.

**Gate G3 — BLOCKING**

- Có một test chứng minh `SIMULATOR` cast **không tự cấp vanilla item/EXP**, không nhầm trigger reward khi reel chưa bite.
- Cancelled cast/reel/hook không tạo catch; không bypass WorldGuard.
- Không vượt gate nếu `setBiteChance(0)` là phương pháp suppression, vì API ấy đã không còn tác dụng.

### Phase 4 — WaterIndex, fish population model, shared/private identity

**Mục tiêu:** Cá có dữ liệu thật phía server nhưng chưa cần hiển thị động hoàn chỉnh.

**Tasks**

- [ ] `WaterIndexService` quét loaded chunks theo budget và invalidate khi nước/chunk thay đổi.
- [ ] `FishKey`, `FishAgent`, zone population quotas và deterministic spawn seed/test harness.
- [ ] `FishCatalogAdapter` tạo species/rarity từ `FishManager`, `RequirementContext` vị trí phao.
- [ ] Spawn `SHARED` và `PRIVATE`, ownership, TTL, cleanup; reserved fish never shared to 2 sessions.
- [ ] Không mutate fish template khi spawn; không gọi `Fish.init()` để gây effects trước catch.
- [ ] Unit/property tests với 1/10/100 viewers và 100–1000 agents simulated, bounded maps.

**Gate G4**

- Fish state và `FishKey` bền vững trong toàn vòng đời agent; private fish chỉ owner có thể reserve.
- Không quét unloaded chunk, không force-load world, không spawn vượt budgets.

### Phase 5 — Packet fish rendering, visibility, custom model fallback

**Mục tiêu:** Người chơi nhìn thấy cá ảo bơi trong hồ ngay cả khi chưa câu.

**Tasks**

- [ ] `PacketTransport` core contract; `PacketEventsTransport` thực thi **trong 1.21 variant/integration adapter**.
- [ ] Virtual entity ID allocator, spawn/metadata/movement/destroy, viewer membership, packet budget và LOD.
- [ ] Vanilla fish model mapping từ `visuals.yml` và random orientation/motion interpolation an toàn.
- [ ] Packet custom `ITEM_DISPLAY` optional, fallback nếu resource pack không chấp nhận.
- [ ] Cleanup khi player đổi world/quit, unload chunk/zone, reload/disable plugin.
- [ ] Test 2 viewers thấy cùng shared agent position/movement; private agent chỉ 1 viewer.
- [ ] Soak test với 30/100 viewers, packet/entity count không tăng sau nhiều vòng join/quit.

**Gate G5**

- Không còn ghost fish sau despawn/quit/reload, virtual entity không phá entity thật.
- Không nhận thưởng từ click/spoof packet fish; zero Bukkit entities spawned cho đàn cá.
- Server không yêu cầu resource pack để cá cơ bản hiển thị.

### Phase 6 — Fish AI, attraction, reservation, reel gameplay

**Mục tiêu:** Có gameplay đủ vòng: thả câu → cá tiếp cận → bite → kéo đúng thời điểm → success/fail state.

**Tasks**

- [ ] Fish movement `WANDER/INTERESTED/APPROACH/BITE/FLEE`, path bounds, obstacle/water checks.
- [ ] Attraction sampling theo `FishManager`/bait/rod/region/competition; cooldown anti spam cast.
- [ ] Reservation atomic + TTL; arbitration khi 2 players dụ một shared fish.
- [ ] Reaction window 26 ticks baseline, grace ticks, early/missed handling, configurable sounds/particles.
- [ ] Session lifecycle/cleanup và optional bounded retarget.
- [ ] Integration test player bait disabled, rod filters, rare private target, biome/time change.

**Gate G6**

- Có animation giống Simulator thay vì vanilla RNG giả; success/fail timing nhất quán.
- Hai player cạnh tranh một `SHARED` agent: **tối đa một người** hoàn tất; private fish không leak.
- Không dùng client packet để quyết định thành công.

### Phase 7 — EMF reward commit, bait/treasure compatibility, stats

**Mục tiêu:** Kết nối success thực với item và hệ thống EMF hiện tại; ưu tiên không double reward.

**Tasks**

- [ ] Simulator success → `FishKey` clone → prepare `Fish` → fire `EMFFishCaughtEvent` once → delivery → post-commit dispatcher.
- [ ] Preserve length, rarity, lore, custom fish item identity, selling value, broadcast và journal.
- [ ] Rod double-catch, XP multiplier, CompetitionBuff và permission/catch-only-in-competition.
- [ ] Bait consumption atomic and only on effective successful fish catch; full inventory and safe drop.
- [ ] `BAIT_LOOT_AGENT` cho `bait.catch-percentage` và native loot-table bridge/fallback cho `only-fish`/treasure.
- [ ] DB stats/reports/leaderboard và reward hooks; no duplicate even with rapid reel/reload.
- [ ] Integration tests with cancelled EMF event and other plugins modifying event priority.

**Gate G7 — RELEASE BLOCKING**

- Fish shown = FishKey delivered; wrong-fish count = **0** trong integration tests.
- Duplicate item/catch event/competition entry = **0** trong spam/race/reload tests.
- Bait và rod usable, không consume charge khi failed; treasure behavior được bảo toàn hoặc zone auto legacy đúng policy.
- DB stats không ghi cho aborted catch; trade/shop/journal vẫn nhận fish.

### Phase 8 — Config polish, visual presets, admin diagnostics, documentation

**Mục tiêu:** Quản trị được trên server thực, GUI/FX không gây khó chịu.

**Tasks**

- [ ] Animation presets common/rare/epic/legendary; actionbar/hologram packet hiệu quả, localized texts.
- [ ] Admin status/zone/debug/reload/off/on; permission, usage, completion, cooldown.
- [ ] Detailed commented `simulator.yml`, `visuals.yml`, `effects.yml`, config examples vùng WorldGuard/cuboid.
- [ ] Sample resource pack mapping và fallback docs (không ship pack nếu chưa được yêu cầu tạo asset).
- [ ] Validation of all keys, migration with backup, status doctor/warnings.
- [ ] Tài liệu admin, troubleshooting, command/permission, performance tune, limitations/Folia notice.

**Gate G8**

- Mọi text user-facing lấy từ locale/config, không hardcode messages trong Java.
- Bad configs không gây crash, missing PacketEvents hoặc pack có fallback rõ ràng.
- Admin có thể cấu hình một hồ mới bằng YAML mà không sửa source.

### Phase 9 — Performance & compatibility hardening

**Mục tiêu:** Xác nhận bản Simulator ổn định để mở với player.

**Tasks**

- [ ] Soak 1/10/30/50/100 active anglers; synthetic packet/latency tests.
- [ ] Profiling ticks, per-player packets, max queue, CPU/heap, leaked UUID/maps, chunk loading.
- [ ] Fault injection DB timeout, packet backend disable, invalid zone, fish catalog reload, inventory full.
- [ ] Tests with custom rods/baits, region boosts 0.5, competition, kill/hunt mode, fish loot selling, no-packet legacy.
- [ ] Verify Paper 1.21.4 and target latest 1.21.x runtime; note 1.20/26-1 compatibility separately, no false claim.
- [ ] Add automated regression in Gradle where possible and manual test scripts for server-only scenarios.
- [ ] Run release candidate with `simulator.enabled: false`, then single private test zone, then gradual rollout.

**Gate G9 — Production Ready**

- Không có P0/P1 open defects (dupe, item loss, crash, protection bypass, native loot double, main-thread DB query).
- No unbounded packet/session/water-index memory growth trong soak; metrics đủ giám sát.
- Có rollback playbook, migration/backup và release notes, test evidence + server version matrix.

### Phase 10 — Optional follow-up, **không nằm trong bản bắt buộc**

- [ ] Cuboid setup admin commands `pos1/pos2/create/delete` với transaction/backup.
- [ ] `Fish ecosystem` theo weather/daytime, seasonal fish migrations, hotspot events — **không** thay rarity/catch parity nếu chưa được duyệt.
- [ ] Tích hợp LeDatPlatform runtime tùy chọn để dùng scheduler/metrics/ledger tại server cụ thể, vẫn giữ independent plugin build.
- [ ] Folia scheduler model dành cho fish shared multiple regions, chỉ claim support sau design+tests.
- [ ] Optional collection milestone UI hoặc spectator displays, không thêm progression economy trái Q5=A.

---

## 18. Comprehensive test matrix

### 18.1. Event ownership / dupe tests (P0)

| Test ID | Kịch bản | Expected |
|---|---|---|
| EVT-01 | Cast ngoài mọi zone | Chỉ legacy EMF pipeline, không tạo session Simulator |
| EVT-02 | Cast trong zone + PacketEvents disabled | Fallback theo config lúc cast, không ClassNotFound/crash |
| EVT-03 | `PlayerFishEvent.FISHING` bị hủy ở `LOWEST` | Không mở session/agent attraction/cue |
| EVT-04 | `FISHING` thành công rồi player đổi zone | Cleanup; không spawn loot ngoài vùng |
| EVT-05 | `REEL_IN` trước khi cá cắn | Fail, 0 EMF event, 0 item, 0 stats |
| EVT-06 | `REEL_IN` đúng bite window | 1 session commit, 1 outcome, correct FishKey |
| EVT-07 | Gửi 2+ reels/same-tick spam | Không vượt 1 reward/caught event |
| EVT-08 | `BITE_READY` hết thời gian | Missed, không reward, release agent đúng policy |
| EVT-09 | Vanilla `CAUGHT_FISH` bất ngờ xuất hiện trong SIM session | Chặn duplicate vanilla item và XP |
| EVT-10 | Hook bị cắn bởi vanilla trong vùng SIM | Không trigger custom success vô điều kiện |
| EVT-11 | Fish rod offhand / hand event null | Đúng rod snapshot hoặc reject an toàn |
| EVT-12 | Session cũ callback sau khi player cast lại | Generation guard ngăn stale reward/visual |
| EVT-13 | EMF caught event bị cancel | 0 item, 0 bait consumed, 0 potion/reward, 0 DB stats |
| EVT-14 | Reload lúc `COMMITTING` | Không thưởng đôi / không dangling session |
| EVT-15 | `CAUGHT_ENTITY` khi target là player/entity khác | Simulator abort, nonfish hooking semantics không bị phá |
| EVT-16 | Đổi rod/bait ngay trước kéo | Policy `CANCEL`; không lấy bonus cần mới |

### 18.2. Fish identity / weights / gameplay tests

| Test ID | Kịch bản | Expected |
|---|---|---|
| FISH-01 | SHARED agent thấy từ 2 client | Cùng world pos, fishKey và behavior; chỉ 1 người có quyền claim |
| FISH-02 | PRIVATE agent tạo cho player A | Client B không nhận spawn packet; B không thể target |
| FISH-03 | FishKey `Epic:Example` đến phao | Item reward là **đúng** fish key ấy, không random loại khác |
| FISH-04 | Player không đủ permission cho rarity | Không bị target cá cấm, kể cả fish shared vẫn thấy |
| FISH-05 | Custom rod `allowed-fish` | Chỉ target fish eligible |
| FISH-06 | Bait modifier với vanilla rod | Thống kê attraction tăng theo policy, consume đúng 1 khi success |
| FISH-07 | Bait modifier với custom rod `allow-baits:true` | Không bị bypass như logic cũ |
| FISH-08 | Custom rod `allow-baits:false` | Không áp dụng boost, không consume |
| FISH-09 | Region boost `0.5`, `1.0`, `1.8`, `15` | Weights số thực thực sự khác, không ép int |
| FISH-10 | `bait.boost:-1`/special modifier | Đúng adapter semantics được chốt, không NaN/infinite weight |
| FISH-11 | Player thay rod trong cast | No reward, no mismatched stat |
| FISH-12 | Rare fish with custom model but player rejects pack | Vanilla fish fallback, reward vẫn đúng |
| FISH-13 | Fish agent mất nước vì block update | Despawn/flee, không path xuyên đất |
| FISH-14 | `Fish.init()` được gọi sau target spawn? | Không: effect/item metadata chỉ prepare tại commit |
| FISH-15 | 100k simulated candidate selection per scenario | Distribution được report, sai lệch nằm trong tolerance phê duyệt |
| FISH-16 | Kéo với ping cao / server lag | Thời gian dựa tick + bounded grace, không cho client spoof timestamp |

### 18.3. Existing EMF feature regression

| Test ID | Kịch bản | Expected |
|---|---|---|
| EMF-01 | Competition đang hoạt động trong/out zone | Bảng xếp hạng chỉ được cập nhật khi catch hợp lệ |
| EMF-02 | Competition chỉ cho phép các world cụ thể | Hook khác world không được ghi điểm |
| EMF-03 | `fishing.catch-only-in-competition:true` | Không lấy EMF fish ngoài competition |
| EMF-04 | `fishing.only-fish:true` + vanilla treasure | Không tráo treasure thành fish; fallback đúng khi bridge chưa ready |
| EMF-05 | `bait.catch-percentage` | Có `BAIT_LOOT_AGENT`/compatible outcome, không đổi cá đã nhìn thấy |
| EMF-06 | EMF fish shop/sell GUI | Item bán được với value/lore/PDC chính xác |
| EMF-07 | EMF journal + unique fish placeholder | Catch Simulator tính một lần, đúng rarity/fish |
| EMF-08 | Fish broadcast rare + double catch | Không broadcast/caught event x2, quantity và UI chính xác |
| EMF-09 | HUNT fish mode | Không tác động đến vanilla fish entities đang được săn |
| EMF-10 | McMMO/AuraSkills hook | Không double loot, bảo toàn configured compatibility |
| EMF-11 | `/emf reload` | Config cũ giữ được; Simulator snapshot swap/cleanup đúng |
| EMF-12 | Legacy region/world permissions + fish toggle | Không mở loophole vì zone mới |
| EMF-13 | Stats enabled/disabled DB | Không NPE khi DB disabled; khi enabled không main-thread block |
| EMF-14 | Deprecated EMF event API bridge | Không phát hơn một lần và cancel semantics được giữ |

### 18.4. GUI, admin, security, config tests

- [ ] `/emf admin simulator status` hoạt động console/player có permission; không permission ra message từ locale.
- [ ] Subcommands có tab complete và `player-only` check; invalid `<zone>`, `NaN`, negative, overflow bị reject.
- [ ] Các GUI EMF hiện có (shop, bait apply, journal): shift-click, double-click, number-key, drag, close/reload với item trong GUI không dupe.
- [ ] Custom model missing/invalid → fallback; unknown fish key → log một lần và skip visual override.
- [ ] Misconfigured `simulator.yml`: negative speed, 0 packet cap, min>max, invalid world, region provider absent, typo enum → controlled error/fallback; không crash.
- [ ] `messages.yml` thiếu key, corrupt MiniMessage, player custom name có tag control → no injection, default fallback.
- [ ] Version migration từ schema cũ, backup và giữ comment/user settings; `config.yml` EMF `version:8` vẫn chạy updater cũ.

### 18.5. Performance / reliability / recovery tests

| Test ID | Kịch bản | Required signal |
|---|---|---|
| PERF-01 | 0 viewers nhưng có 5 pond zones | Không spawn/tick đàn cá vô nghĩa |
| PERF-02 | 30 viewers tại 1 hồ | Không vượt per-viewer fish cap và packet cap |
| PERF-03 | 100 viewers tại 1 hồ | Queue bounded, LOD giảm trước, no tick flood |
| PERF-04 | Teleport 50 viewers cùng lúc | Spawn packet burst được queue/rate limit |
| PERF-05 | 100 player rapid cast/reel trong 2 phút | Session/map count quay về baseline sau cleanup |
| PERF-06 | Chunk unload/reload lặp lại | WaterIndex không giữ stale blocks/agents; no ghost fish |
| PERF-07 | World unload/reload/plugin disable | Không orphan task/packet viewer mapping |
| PERF-08 | 1.000ms MySQL latency injection | DB không block main fishing tick; bounded backlog |
| PERF-09 | File/database disconnect/reconnect | Dirty revisions không xóa update mới, có retry/alert |
| PERF-10 | Packet backend unavailable mid-session | Abort an toàn và legacy cho casts mới |
| PERF-11 | Reload liên tục 10 lần | Không double listeners, tasks, singleton state |
| PERF-12 | Resource pack deny / player with vanilla client | Cá default vẫn nhìn thấy |
| PERF-13 | Server bị lag tick 200ms | Bounded reaction grace/timeout nhất quán, không exploit |

### 18.6. Soak/release readiness criteria

- **Zero tolerance:** item/reward double, cancelled catch vẫn reward, region bypass, ghost fish sau quit, invalid item NBT, inventory loss, DB overwrite do default cache.
- **Metrics required:** p50/p95/p99 MSPT delta, packet rates, queue max/drop ratio, heap growth, active session trend, viewer count, task count.
- **Confidence:** Unit tests cho resolver/weights/states; MockBukkit giả lập được thì dùng cho logic thuần; server thật cần integration test event/packet/world; không thay integration test bằng unit test.
- **Validation versions:** ít nhất Paper 1.21.4 và một bản 1.21.x mới được xác nhận trong target runtime; nếu không test variant 1.20/26-1 thì không claim Simulator support ở đó.

---

## 19. Technical decisions / ADR log

### ADR-001: Chỉ một engine sở hữu cast

**Decision:** `EngineMode` cố định từ cast, router duy nhất.  
**Why:** Không thể chạy song song `FishingProcessor` vanilla + Simulator cùng loot event mà không có nguy cơ double.  
**Alternative rejected:** Thêm listener Simulator độc lập song song và dùng priority để "may mắn" chặn event.

### ADR-002: Cá có danh tính trước khi bị câu

**Decision:** `FishKey` gắn agent tại spawn, clone `Fish` tại commit.  
**Why:** WYSIWYG đúng nghĩa; tách visual simulation với mutable fish/reward.  
**Tradeoff:** Phân phối cá chịu ảnh hưởng spawn/attraction; cần population tuning thay vì hứa tỷ lệ vanilla y hệt.

### ADR-003: Shared population + limited private special fish

**Decision:** Một shared pond thực, cá cá biệt private theo viewer.  
**Why:** Vừa tạo môi trường chung vừa hỗ trợ rare discovery/rod/bait đặc biệt.  
**Tradeoff:** Global private cap để tránh bộ nhớ/băng thông phình theo player.

### ADR-004: Không cưỡng ép resource pack

**Decision:** Packet virtual vanilla fish mặc định, custom ItemDisplay chỉ nếu client có pack hợp lệ.  
**Why:** Server có thể đưa Simulator lên production trước khi hoàn thành assets, người chơi vanilla không mất khả năng câu.

### ADR-005: Không thêm progression mới

**Decision:** Không triển khai minion/prestige/level/rod upgrades.  
**Why:** Q5=A: giữ EMF và chỉ nâng engine câu cá.

### ADR-006: Giữ `FishingType` public API

**Decision:** Thêm `EngineMode`, không đổi nghĩa `FishingType.VANILLA`.  
**Why:** `FishingType` đang phân biệt `VANILLA/LAVA/VOID` và được addon dùng làm requirement; Simulator là mode của nước vanilla, không phải liquid category mới.

### ADR-007: Fail-open visual, fail-closed permission

**Decision:** Visual có thể fallback; zone provider/permission check không fail open.  
**Why:** Thiếu pack không nên phá câu cá, thiếu bảo vệ vùng không được mở cửa khai thác.

### ADR-008: No forced DB migration

**Decision:** Ephemeral agents/sessions trong memory, stats/competition vẫn dùng EMF database.  
**Why:** Giảm migration risk và cho phép rollback bật/tắt engine, giữ fish identity cũ.

### ADR-009: Native hook spike trước khi viết phần lớn Simulator

**Decision:** Phase 3 là blocker về suppress native loot.  
**Why:** Nếu không xử lý chính xác `FishHook` / event contract theo Paper version, mọi module AI/packet phía trên có nguy cơ tạo dupe.

### ADR-010: Paper-only release đầu tiên

**Decision:** Target Paper 1.21.x/Java21, no Folia claim.  
**Why:** Fish shared trên nhiều vùng và `WorldGuard`/entity lifecycle đòi region thread ownership phức tạp; chuyển sang Folia chỉ sau khi thiết kế scheduler đúng mô hình.

---

## 20. Release train và milestone deliverables

| Milestone | Phases | Artifact tối thiểu | Ship policy |
|---|---|---|---|
| **M0 — Safe refactor** | 0–1 | EMF build + bugfix tests + DB guard | Có thể deploy legacy sau regression |
| **M1 — Routing proof** | 2–3 | Config/zones/router + native hook test report | Simulator vẫn disabled cho public |
| **M2 — Living pond** | 4–5 | Agent/quần thể/cá packet bơi, no reward | Test pond/staff only |
| **M3 — Playable Simulator** | 6–7 | Bite/reel, reward, bait/rod/competition | Closed beta, theo dõi dupe/DB |
| **M4 — Production candidate** | 8–9 | Config docs, visuals, metrics, profiling, release ZIP/JAR | Canary zone → gradual enable |
| **M5 — Optional extensions** | 10 | Admin region builder/Folia exploration/advanced features | Chỉ khi user duyệt riêng |

### 20.1. Khi nào không được release?

- Event suppression native chưa được chứng minh bằng test Paper.
- Stats/reward còn bị ghi cho `EMFFishCaughtEvent` đã hủy.
- Có đường `PlayerFishEvent` hoặc economy/database query đang block main.
- Tắt Simulator gây mất EMF cá, bait, competition, shop, journal hoặc addon API.
- Lượng packets/render/scan không bounded theo config.
- Reload hay quit làm session/cá packet tồn tại sau khi cần dọn.
- Thiếu rollback hướng dẫn hoặc chưa test config migration.

### 20.2. Definition of Done của một phase

- [ ] Source compile ở target variant, lint/test hiện có pass.
- [ ] New module có unit tests (logic thuần) hoặc integration scenario (Paper event/packet).
- [ ] Config key có comment, validation, fallback, locale messages.
- [ ] Không gọi Bukkit API ngoài owner/main thread; không DB file I/O sync.
- [ ] Không tạo duplicate reward/side effects ở failure paths.
- [ ] Có changelog ghi rõ files/classes sửa, nguy cơ còn lại.
- [ ] Có metrics hoặc test log chứng minh behavior claim.
- [ ] Có rollback note cho release cần compatibility.

---

## 21. Hướng dẫn giao Codex triển khai từng phase (không được nhảy phase)

> Mẫu này có thể copy làm task prompt. Đính kèm `MASTER_IMPLEMENTATION_PLAN_EvenMoreFish_Simulator.md`, original `EvenMoreFish.zip`, review Markdown và API docs nếu cần.

**Codex implementation contract:**

1. **Đọc source thật trước**, rà từng method/handler/dataflow trong phase được giao. Không dựa hoàn toàn vào tên class trong plan để khẳng định code đã có.
2. Giữ namespace/package/API public của EMF; không thay command/permission/item NBT/schema DB trừ khi được phase yêu cầu và có migration.
3. Chỉ thực hiện phase được giao và các bugfix **trực tiếp cần thiết**. Không tự thêm minion/prestige/tension game/economy.
4. Sau mỗi phase báo: files changed, code path, test commands, test results, regressions, remaining risks và checklist gate.
5. Không báo "production-ready" chỉ vì `gradle build` pass; yêu cầu runtime tests với Paper/PacketEvents/world hooks.
6. Dùng `Adventure/MiniMessage`, config/messages tách riêng, no main-thread DB/block scans, no Folia claim.
7. Nếu event/version API khác giả định plan, **sửa lại ADR + test evidence**, không cố code workaround reflection/NMS tùy tiện.
8. Nếu gặp điểm chưa quyết định làm thay đổi gameplay, dùng **defaults của §22** và ghi assumptions vào PR; đừng bỏ ngang công việc chỉ vì lựa chọn nhỏ chưa hỏi.

**Example handoff task:**

> "Triển khai **Phase X** trong `MASTER_IMPLEMENTATION_PLAN_EvenMoreFish_Simulator.md` từ source EvenMoreFish hiện tại. Đọc mã và xác minh contract của phase. Chỉ sửa những module trong scope; không code phase khác. Giữ legacy behavior khi Simulator disabled. Viết unit/integration tests, chỉ rõ Gradle task chạy, xuất changelog, acceptance checklist G[X], các issue/blocker còn tồn tại. Không tự nhận Folia support và không sửa schema DB production nếu chưa có migration." 

---

## 22. Những quyết định chưa được user chỉ định — defaults đã chọn

| Quyết định | Default gợi ý | Lý do |
|---|---|---|
| Loại zone | WORLD + CUBOID + WG/RedProtect | Không bắt buộc WorldGuard ở mọi server |
| Vị trí xác định zone | HOOK_LOCATION | Tránh kéo phao qua biên vùng để bypass |
| Fallback thiếu PacketEvents | LEGACY_EMF | Giữ toàn plugin dùng được |
| Bật Simulator mặc định | `false` | Chỉ enable sau test/cấu hình world |
| Cá riêng | Chỉ Epic/Legendary, quota 2/player | Q2=C nhưng cần kiểm soát packets |
| Rod đổi giữa lúc câu | CANCEL | Chống bait/luck/double exploit |
| Consume bait | Thành công và bait có tác động | Không phạt người chơi vì hụt |
| Reel quá sớm | Fail, cá bỏ chạy | Q3=A, giữ tương tác đơn giản |
| Reaction window | 26 ticks + 3 grace | Starting balance dễ tùy chỉnh |
| Custom models | Optional, vanilla fallback | Q4=B |
| Cinematic | Ngắn, rarity-based, no camera lock | Tránh gây khó chịu khi spam |
| `only-fish` / native treasure | Preserve hoặc legacy fallback khi bridge chưa sẵn | Không làm mất loot hiện có |
| DB mới cho fish population | Không | Ephemeral state, giữ existing EMF DB |
| Folia | Không claim trong initial release | Cần region model thiết kế riêng |

**Lưu ý cho user:** Nếu muốn thay đổi default, sửa phần config/ADR trước khi Codex bắt đầu từng phase; không cần điều tra lại toàn bộ requirement Q1–Q5.

---

## 23. Sources / evidence boundaries

### Đã kiểm tra trực tiếp từ file bạn gửi

- `EvenMoreFish.zip`: `even-more-fish-plugin/src/main/java/com/oheers/fish/fishing/FishingProcessor.java`, `Processor.java`, `FishManager.java`, `Rarity.java`, `BaitHandler.java`, `CustomRod.java`, `FishingHookHolograms.java`, `EMFFishListener.java`, `EventManager.java`, `EvenMoreFish.java`, `MainConfig.java`, `MessageConfig.java`, `DataManager.java`, `even-more-fish-api/.../FishingType.java` và `EMFFishCaughtEvent.java`; Gradle/resource configs.
- `RozenFishing-1.0.0-SNAPSHOT.jar`: tham khảo bytecode về packet fish, moving schools/reservations/water indexing/UX; không có source chính thức để đối chiếu thiết kế nội bộ với độ chắc như EMF Java source.
- `FISHING_PLUGIN_COMPARISON_REVIEW.md`: static audit trước đó, những rủi ro cần chữa từ EMF và các ý tưởng comparator.

### API chính thức dùng để kiểm tra hướng tích hợp (không coi là proof production)

- Paper 1.21.4 `PlayerFishEvent` — `Cancellable`, `getHand()`, `getHook()`, `getExpToDrop()`/`setExpToDrop()` và event states: <https://jd.papermc.io/paper/1.21.4/org/bukkit/event/player/PlayerFishEvent.html>
- Paper 1.21.4 `FishHook` — wait/lure timers, `setBiteChance` deprecated/no effect ở Minecraft mới: <https://jd.papermc.io/paper/1.21.4/org/bukkit/entity/FishHook.html>
- PacketEvents docs, gửi packet tới player bằng wrappers, kiểm tra wrapper đúng Minecraft version: <https://docs.packetevents.com/sending-and-simulating-packets/>

### Chưa xác nhận tại thời điểm viết

- Runtime event order, khả năng triệt native fish loot của từng Paper minor release và tương tác mcMMO/AuraSkills/protection plugin.
- Build success của checkout/Gradle conventions; artifact ZIP có source và outputs nhưng chưa chạy build trong lần lập kế hoạch này.
- Actual network bandwidth, MSPT/TPS, packet compatibility cho custom ItemDisplay và recovery khi PacketEvents reload.
- Odds distribution cuối sau population+attraction, phản hồi từ người chơi và economy balance.

**Do đó**: G3 và G7 là gate bắt buộc; không được diễn giải plan như bằng chứng đã triển khai xong.

---

## 24. Final production checklist

- [ ] Simulator toggle off → EMF legacy hoàn toàn, có parity/regression evidence.
- [ ] Only selected world/region activates; overlap deterministic; missing region provider fail closed.
- [ ] Single event router, one owner engine per cast, validated Paper FishHook suppression.
- [ ] Agent identity → fish clone → EMF item: consistent 100% trong test.
- [ ] Shared fish visible to nearby players; private fish only to owner, reservation atomic.
- [ ] PlayerFishEvent cancellation and EMFFishCaughtEvent cancellation honored.
- [ ] Không double vanilla loot/EXP, không double EMF reward, no extras on failed catch.
- [ ] Bait+custom rod correctly combined; no bait consumed on miss; rod swap exploit blocked.
- [ ] `only-fish`, vanilla treasure, bait catch và competition compatibility được test/fallback minh bạch.
- [ ] Existing fish items/shop/journal/leaderboard/database/addons unaffected.
- [ ] Water scanning limited to loaded chunks, bounded time and memory.
- [ ] Renderer has per-viewer/global packet budget and LOD, no ghost entities on quit/reload.
- [ ] Visuals default vanilla; resource pack model optional/fallback.
- [ ] Config migration validates input; user-facing messages in locale/MiniMessage.
- [ ] DB reads/writes async; flush dirty generation-safe; clean graceful disable.
- [ ] Paper 1.21.x integration + stress results attached, no false Folia claim.
- [ ] Release artifacts, backup/rollback instructions and changelog ready.

**Kết luận:** Đề xuất này giữ nguyên sản phẩm EMF nhưng thay trải nghiệm câu cá **chỉ trong zone đã chọn** bằng một simulator có cá ảo và server-authoritative gameplay. Ưu tiên **đúng logic, tương thích và không dupe** trước khi đánh bóng cinematic.

---

## Phase 07 — implementation status (2026-10-11)

Phase 07 source includes `FishAiDirector.reelWithCommit()`, EMF `FishKey` item preparation, cancellable event, gated delivery, bait/rod bonuses, compatibility fallback and stats deferral by event identity. See `PHASE_07_IMPLEMENTATION_REPORT.md` for exact file paths and tests.

**Gate G7 remains NOT PASSED:** Gradle/Paper validation is unavailable in this environment; BAIT_LOOT_AGENT and native treasure compatibility are deliberately handled through legacy fallback, not simulated packets. Keep reward-bridge disabled on production.

## Phase 08 — implementation status (2026-10-11)

- Phase 8 config/FX/HUD/cinematic/admin guide implemented in source; independently tested with test-only Bukkit stubs.
- New visual files: `simulator/effects.yml`, `SimulatorEffectsSettings`, `SimulatorEffectsConfigLoader`, `SimulatorEffectsService`, `CatchArc`, `PacketCatchCinematicService`.
- No new real entity or reward logic; only the current angler receives the packet cinematic.
- See `PHASE_08_IMPLEMENTATION_REPORT.md` and `qa/PHASE_08_PAPER_TEST_MATRIX.md` for details, including known limitations and runtime test gates.
- **Gradle full compile and Paper/PacketEvents client verification remain blocked/unexecuted.** Do not treat Java parser/mock smoke successes as Gate G3/G5/G7/G8 runtime sign-off.
