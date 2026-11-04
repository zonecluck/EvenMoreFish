# 🎣 EvenMoreFish Simulator V2 — PREMIUM FISH PACKET & CINEMATIC UPGRADE
## MASTER UPGRADE PLAN / Product Specification / Technical Design / Phased Delivery

> **Ngày lập:** 2026-10-11  
> **Baseline đã đọc:** `EvenMoreFish-Simulator-Phase091-source.zip` (tích lũy Phase 01–9.1).  
> **Định hướng được duyệt:** Q1=D · Q2=D · Q3=D · Q4=E · Q5=D.  
> **Trạng thái:** **PLAN ONLY — CHƯA THAY ĐỔI SOURCE / CHƯA VIẾT IMPLEMENTATION CODE**.  
> **Platform:** Paper 1.21.4+, Java 21, Gradle Kotlin DSL, PacketEvents tại adapter hiện hữu; không tuyên bố Folia support.  
> **Nguyên tắc:** giữ toàn bộ EMF, bảo vệ `EMFFishCaughtEvent`/FishKey/reward; visual là lớp phụ, không tự quyết định loot.

---

## 0. Executive summary

Biến phần Fish Packet hiện tại từ **cá ảo bơi → tiến gần phao → cắn → kéo → một đường cinematic** thành trải nghiệm **Fishing Simulator Premium** gồm:

1. **Living Fish Ecosystem:** bơi theo đàn, leader/follower, rẽ hướng mềm, tránh vật cản, phân tán khi bị tác động, trở lại đàn; personality khác nhau theo fish/rarity.
2. **Environmental Awareness:** điều chỉnh hành vi theo vùng hồ, độ sâu, biome, thời tiết, chu kỳ ngày đêm và dòng nước *trong giới hạn dữ liệu đã index*, không scan toàn world.
3. **Encounter System:** rare presence, ripple warning, shadow/ghost/golden encounter, school event; vẫn dùng FishKey của EMF, không biến biểu diễn thành loot giả.
4. **Water FX:** ripple, bubble trail, splash, bait lure pulse, subtle disturbance, whirlpool giả lập bằng particle/packet; không sửa block, không tạo dòng nước thật.
5. **Professional HUD:** một bộ điều phối UI duy nhất quyết định channel/priority/thời hạn, khắc phục chữ chồng nhau như ảnh người dùng cung cấp; người chơi chỉnh từng layer bằng GUI.
6. **Rarity Cinematic Director:** timeline nhiều giai đoạn, hiệu ứng âm thanh đồng bộ, virtual fish motion, camera-friendly reveal, cá riêng có script riêng. Legendary/Mythical ấn tượng hơn nhưng có skip/reduced FX.
7. **Performance + quality:** interest-based visibility, LOD, fixed/global budgets, per-viewer limits, scene arbitration, queue cleanup, fail-safe, metrics và QA.

**Phạm vi vẫn giữ từ MASTER_IMPLEMENTATION_PLAN trước:** Simulator chỉ trong zone được chỉ định, ngoài zone giữ EMF vanilla; SHARED + PRIVATE fish; dùng vanilla model mặc định và custom model tùy chọn; không thêm tiền, minion, prestige, bắt kéo liên hoàn hoặc forced minigame.

### 0.1. Product principles (P0)

- **Đẹp nhưng rõ gameplay:** tín hiệu cá cắn thật luôn khác rõ hiệu ứng cá mới phát hiện mồi. Không dùng fake bite gây nhầm lẫn mặc định.
- **Visual follows authoritative state:** state từ server → FX. FX không bao giờ làm `CatchCommit`, ghi DB, trừ mồi, spawn reward.
- **Một fish, một identity:** cinematic/reveal dùng cùng `FishKey`, rarity, size/item metadata đã commit; không reroll.
- **Không ép cinematic:** client không mất quyền điều khiển, camera không bị xoay/teleport, có nút skip, giảm hiệu ứng và tắt hoàn toàn.
- **Người khác không bị spam:** SHARED population nhìn chung; PRIVATE encounter và cinematic chỉ gửi viewer hợp lệ. Broadcast bên ngoài chỉ admin bật và có cooldown.
- **Giữ đường fallback:** lỗi packet, region, custom model hoặc hiệu ứng không phá legacy EMF; những phiên đã được takeover phải fail-closed chứ không tự phát thưởng legacy muộn.
- **Chưa release:** full Gradle build + G3/G5/G7 Paper runtime + load test thật là điều kiện cứng.

---

## 1. Requirement summary

| Mã | Nhu cầu đã chốt | Quyết định |
|---|---|---|
| R-01 | Mở rộng cả visual, AI, encounter, UX | Toàn bộ, có toggle và giới hạn |
| R-02 | Cá hiếm cinematic theo fish/rarity | Profile rarity + override `rarityId:fishId` |
| R-03 | HUD đa lớp | Toggle theo player, priority, channel arbitration |
| R-04 | Schooling + environment + rare encounter + water effects | Bật/tắt riêng từng nhóm |
| R-05 | Cinematic tăng dần theo độ hiếm | Common nhẹ → Rare rõ → Epic cao trào → Legendary/Mystic nhiều cảnh |
| R-06 | Không thay đổi gameplay bắt cá chính | Bite/reel một lần theo engine hiện có |
| R-07 | Giữ config/data/compat EMF | Không phá migration `version` gốc; Simulator config-version riêng |
| R-08 | Performance | Có điều kiện tắt bớt FX/AI/LOD khi quá budget |
| R-09 | Admin tools | Preview một timeline/encounter/đàn cá mà không phát item thật |
| R-10 | Gameplay fairness | Không phóng đại rarity/đánh lừa người chơi khi thực tế không có fish hợp lệ |

### 1.1. Player flow kỳ vọng

1. Người chơi tới zone Simulator, quan sát những đàn cá (shared), một số cá riêng (private) khi hợp lệ.
2. Bầy cá chuyển động tự nhiên; có thể nhìn thấy ripple nhỏ, thỉnh thoảng bubble trail, chuyển biến theo ngày/đêm.
3. Người chơi thả cần: phao có lure pulse rất nhẹ, cá đủ điều kiện chú ý và đến gần.
4. Cá đến gần: tín hiệu `INTERESTED/APPROACHING` được *thể hiện tại mặt nước*, HUD trung tâm không bị chiếm.
5. Cá thật sự cắn: một tín hiệu âm thanh đặc trưng + đúng một thông báo hành động nổi bật + thanh phản ứng rõ ràng.
6. Người chơi kéo đúng lúc: commit cá EMF, hệ thống phát cinematic theo rarity/fish; nhận item/stats/reward đúng một lần.
7. Nếu hụt: ripple dịu, cá lặn/bỏ chạy, HUD biến mất; không làm giả phần thưởng.
8. Nếu player tắt cinematic: bắt cá và nhận thưởng vẫn diễn ra như thường.

### 1.2. Admin flow kỳ vọng

- `/emf admin simulator doctor` báo packet health, G3/G5/G7, budgets, animation queue, active schools/encounters, HUD conflicts.
- `/emf admin simulator preview <rarity> [fishKey]` dựng scene **visual-only** cho chính admin, không can thiệp EMF reward.
- `/emf admin simulator fx test <preset>` kiểm tra wave/particle/sound có rate limit.
- `/emf admin simulator school spawn <preset>` tạo đàn preview có TTL, không tăng reward odds.
- `/emf admin simulator encounter preview <id>` minh họa encounter, không trao cá.
- `/emf admin simulator cinematic stop` kết thúc scene của chính admin.
- `/emf admin simulator reload` validate toàn bộ cấu hình mở rộng theo candidate snapshot, commit đồng bộ hoặc từ chối toàn bộ.
- Các command **đề xuất** trên chưa được hiện thực; phải tích hợp cây lệnh EMF hiện có, có permission, tab complete, player-only check và locale message.

### 1.3. Out of scope

- Không mở thêm hệ progression lớn, tension bar bắt buộc, auto-fishing/minion, prestige/currency.
- Không tự chuyển camera sang spectator, ép `Player#setRotation`/teleport để giả lập rung camera.
- Không dùng NMS/reflection tùy tiện hoặc chuyển sang Folia khi chưa thiết kế scheduler riêng.
- Không gửi cinematic của cá PRIVATE tới mọi người.
- Không đưa vật phẩm 3D của resource pack thành dependency bắt buộc.
- Không hứa hỗ trợ cross-server synchronized fish population trong bản này.

---

## 2. Audit baseline Phase 9.1 — đã có gì / thiếu gì

> Phần này căn cứ các file **thực sự có trong ZIP Phase 9.1**; mục sau là thiết kế đề xuất, không phải chức năng đã được code.

| Hệ thống | Hiện có trong source | Khoảng trống cho Premium |
|---|---|---|
| `FishAgent` | State WANDERING/RESERVED/INTERESTED/APPROACHING/BITE_READY/FLEEING/CAUGHT/DESPAWNED | Group identity, personality, steering/formation, animation metadata, LOD intent |
| `FishPopulationService` | SHARED/PRIVATE, agent lifetime, reservation | School spawner, population composition, shared event anchors |
| `WaterIndexService` | Index ô nước theo budget | Độ sâu/biome snapshot rẻ, environmental buckets, obstacle-aware behavior |
| `FishAiDirector` | Tìm phao, tiếp cận, cắn, reaction window | Interest nuances, schooling interaction, personality-driven timing có giới hạn |
| `PacketVisibilityService` | Viewer visibility, budget, destroy-first, cleanup retry | Distance LOD, animation priority, packet coalescing, shared budget with scene FX |
| `PacketCleanupQueue` | Giữ orphan IDs chờ destroy | Reuse cho renderers/FX mới, không phát sinh hệ thống cleanup riêng |
| `SimulatorEffectsService` | Cue theo phase, HUD actionbar 5 tick/lần, opt-out session | UI arbitration, persistence preference, split alerts khỏi actionbar |
| `SimulatorEffectsSettings` | 4 preset fixed, rarity mapping, fish override | Timeline cinematic, scene profile, user FX quality modes, cue mix |
| `CatchArc`/`PacketCatchCinematicService` | Một virtual fish theo một cung từ phao đến player | Multi-stage sequence, leap/twist/reveal, scene cancellation, blending |
| `simulator/effects.yml` | HUD và preset sound/particle/arc cấu hình | Thêm schema có migration; không phá key/behavior cũ |
| `simulator/visuals.yml` | Vanilla fish models; ITEM_DISPLAY là placeholder fallback | Renderer `ItemDisplay`/resource-pack bản versioned có kiểm thử |
| `SimulatorModule` | Router, native hook takeover, callback, admin gate | Orchestrator mỏng; tránh nhồi scene logic; event bus nội bộ typed |
| `SimulatorCatchService` | Chuẩn bị và giao item, EMF event, revision guard | Cosmetic snapshot hậu commit, bảo đảm fx failure không đổi kết quả |
| Release gates | Chưa full Gradle compile; G3/G5/G7 chưa kiểm chứng runtime | Bắt buộc làm trước khi mở public Simulator |

### 2.1. Vấn đề UI từ ảnh người dùng

- Ảnh đầu cho thấy **dòng chữ thông báo gần/cắn chồng lớp**, khó đọc và phân biệt. Không kết luận nguồn là một class duy nhất vì cần đo runtime với các plugin UI khác.
- Ảnh dưới nước cho thấy thanh thời gian nằm lệch trên không gian gameplay; đây là cơ sở đề xuất cho **layout và ownership**, không chứng minh client bug.
- Thông báo kiểu fish spit/trừ máu trong ảnh nhắc tới nhu cầu thiết kế cue mạnh nhưng phải kiểm tra **tương tác gameplay với plugin khác**; không mặc định biến cosmetic thành sát thương.

### 2.2. Điều cần giữ nguyên khi refactor

- `SimulatorModule.onFishEvent(...)` là lối vào thuộc một listener `FishingProcessor`; **không đăng ký một `PlayerFishEvent` listener song song** để cấp reward.
- `FishKey` chỉ xác định từ EMF catalog; không tạo fish giả/rarity giả.
- `SIMULATOR` và `LEGACY_EMF` được khóa theo phiên câu; không tự đổi chế độ giữa chừng.
- Các callback `phase()` và `finished()` có thể phát signal *read-only* sang presentation director, không cấy reward logic vào FX.
- `EMFFishCaughtEvent`, competition, bait, rod, stats giữ contract và chuẩn chống trùng đã xây ở Phase 7/9.1.

---

## 3. UX DESIGN SYSTEM — giải quyết HUD chồng chữ

### 3.1. Đặt ra một `PresentationArbiter`

Không có class nào được tự ý gửi ActionBar/Title/BossBar cho phần Simulator bên ngoài presentation owner. Các nguồn `ai phase`, `reward`, `environment`, `encounter`, `cinematic` gửi **UI intent**.

**Intent fields:** `playerId`, `sessionId`, `channel`, `priority`, `source`, `payload`, `expiresAt`, `dedupeKey`, `cancelOn`, `styleProfile`.

**Priority (cao → thấp):**

1. `CATCH_CONFIRMED` / `LEGENDARY_REVEAL` (chỉ khi commit thành công).
2. `BITE_READY` — người chơi cần kéo thật ngay.
3. `CATCH_RESULT_FAILURE` / `EARLY_REEL` — phản hồi ngắn.
4. `RARE_ENCOUNTER` — chỉ khi không có bite.
5. `APPROACHING` — chủ yếu cue tại phao, không chiếm UI trung tâm.
6. `AMBIENT_HINT` — thông tin nhẹ, có thể bỏ qua.

**Quy tắc hiển thị:** một thời điểm tối đa một message thuộc cùng channel; message mới ưu tiên thấp không đè message cao. `BITE_READY` luôn preempt `INTERESTED`; bản cũ hết hiệu lực ngay, không tự hiện lại sau khi player đã reel.

### 3.2. Phân phối các kênh UI

| Channel | Nội dung ưu tiên | Vị trí/cách thể hiện | Giới hạn |
|---|---|---|---|
| Near-bobber | Bubble, attraction, small status | VFX world-space + packet text optional | Chỉ 1 anchor/hook, cull theo distance |
| ActionBar | Thanh bite timing, chỉ dẫn kéo ngắn | Một owner trong Simulator, component cache | 4–5 lần/giây hoặc khi state đổi |
| Title/Subtitle | Rare encounter và reward reveal | Không dùng cho approaching thường | Có interval/cooldown, không spam mỗi tick |
| BossBar | Optional long encounter/event progress | Mặc định **OFF** | Một bossbar/session; dọn sau timeout |
| Sound cue | BITE, CATCH, FAIL và cinematic stinger | Spatial/private, cooldown | Một cue chính theo transition |
| GUI | `/emf` settings, preview, help | Tận dụng menu EMF | Custom InventoryHolder/GuiSession safety |
| Chat | Kết quả, bảng competition, thay đổi setting | Theo locale của EMF | Không chat spam mỗi frame |

**Layout đề xuất:** `WAITING` không hiện countdown trung tâm; `APPROACHING` là gợn nước nhỏ quanh phao; `BITE_READY` hiển thị một ActionBar: **`🎣 CÁ CẮN!  ▰▰▰▰▱▱▱▱  1.3s · Chuột phải`**. Tránh subtitle cảnh báo + HUD khác chồng lên vị trí trung tâm như ảnh. Kết quả câu có một title nhỏ theo rarity, rồi chuyển về state idle.

### 3.3. Preset UI của player

- `OFF`: không FX/HUD Simulator, gameplay vẫn chạy.
- `MINIMAL`: bite sound + ActionBar bite, rare notification nhẹ.
- `BALANCED` (**mặc định đề xuất sau production validation**): ActionBar, near-bobber cues, short reveal, Common reduced.
- `CINEMATIC`: đầy đủ cinematic Epic/Legendary + haptic-feel ảo qua visual/sound, không xoay camera.
- `REDUCED_MOTION`: không screen flash/zoom/shake, giảm particle/arc/scene speed; bite cue vẫn rõ.
- Từng toggle: `actionbar`, `bite-alerts`, `world-hints`, `ambient-fish-fx`, `catch-cinematic`, `rare-announcements`, `sounds`, `particles`, `bossbar`.
- Preferences lưu theo UUID, `serverDefaults` fallback; nạp async, áp dụng trên owner thread; logout flush có timeout, không ghi file mỗi toggle liên tục.
- Xung đột UI với plugin khác: không có API chung để đảm bảo exclusive ActionBar; cung cấp chế độ chuyển từ actionbar sang near-bobber cue/bossbar hoặc `minimal`, logging debug để admin điều chỉnh.

### 3.4. Accessibility & localization

- Trạng thái quan trọng không chỉ báo bằng màu (có icon/từ khóa/âm thanh).
- Text ngắn, không italic không chủ ý, contrast cao trong nước tối.
- MiniMessage cho text từ config/locale, escape input ngoài, không parse fish name do player nhập trực tiếp.
- Dùng tiếng Việt mặc định theo locale, có fallback; mọi text thêm vào 18 locale bằng key cụ thể, không hardcode Java.
- Client không có resource pack vẫn thấy cue vanilla và text dễ hiểu.
- Khi player đang mở GUI hoặc dùng dialog, có policy ưu tiên không chen title/camera VFX.

---

## 4. LIVING ECOSYSTEM — đàn cá và chuyển động

### 4.1. `FishSchool` domain model

Một đàn là **nhóm logical FishAgent đang tồn tại** (không nhân số entity chỉ để làm hiệu ứng). Các thuộc tính dự kiến:

- `schoolId`, `zoneId`, `worldId`, `leaderFishId`, `memberFishIds`, `speciesProfileId`.
- `anchor`, `heading`, `speed`, `formation`, `roamRadius`, `currentBehavior`, `lastDecisionTick`.
- `visibilityPolicy`: shared/private, không trộn owner giữa các cá.
- `spawnedAt`, `expiresAt`, `seed`, `environmentBucket`.
- FishKey gắn với từng agent; đàn có thể có nhiều rarity nhưng vẫn obey catalog/conditions.

**State nhóm:** `CRUISING → TURNING → SCATTERED → REFORMING`; các state tương tác với fish event `INTERESTED/APPROACHING` nhưng không thay thế state machine authoritative trong `FishAiDirector`.

### 4.2. Steering nhẹ — không full Boids N²

- Leader chọn waypoint hợp lệ trong các `WaterPoint` đã index.
- Follower chọn offset sau leader với jitter deterministic; chỉ đọc tối đa `k` neighbor gần qua spatial hash/chunk bucket.
- Separation nhỏ để không chồng model, collision avoidance dựa trên cell/hướng, không raytrace liên tục.
- Có easing khi rẽ, blend heading và swim speed, tránh teleport step lớn gây giật.
- Nếu ô nước không hợp lệ/chunk unload: hủy route và chọn cell khác hoặc despawn phù hợp; không force-load.
- `SchoolMotionScheduler` xử lý batch với max steps/tick; visual LOD quyết định tần suất packet, không nhất thiết tần suất simulation.
- Khi một cá bị reservation, detach khỏi group motion (không cho school steering ghi đè `APPROACHING`).
- Khi một cá bị bắt: shared fish biến mất từ các viewer; các cá còn lại chuyển direction nhẹ, không spawn reward thêm.

### 4.3. Behavior profiles

| Personality | Hiệu ứng cảm nhận | Chi tiết kỹ thuật |
|---|---|---|
| CALM | Bơi mượt theo đàn, tiếp cận chậm | Small wandering radius, low turn rate |
| CURIOUS | Đi gần phao, quan sát rồi quyết định | Timed orbit, không phát fake `BITE_READY` |
| SHY | Dễ giật mình, quay đi khi player kéo hụt | One-cell flee, cooldown retarget |
| AGGRESSIVE | Bơi nhanh đến phao | Attraction window ngắn có clamp fairness |
| NOCTURNAL | Hoạt động dễ thấy vào ban đêm | Spawn/display animation theo time bucket |
| DEEP_DWELLER | Sống ở tầng nước sâu, ít lên mặt | Depth eligibility + route constraints |
| MYTHIC | Quay vòng, glow/trail tinh tế, xuất hiện hiếm | High budget class, viewer-specific cues |

Personality là *profile mapping*, không thêm entity class/plugin hook riêng cho từng cá. Hành vi chỉ ảnh hưởng odds và bite timing khi chính sách gameplay cho phép, không ngầm sửa rarity weight hoặc tăng loot.

### 4.4. Fish population constraints

- Giữ mặc định Phase 9.1 trước khi benchmark: max agents 160, 48/zone, 20 visible/viewer, 800 ambient packet/tick.
- Đề xuất giai đoạn đầu: tối đa 4–6 cá/đàn, 6 đàn/zone (không phải cộng thêm agent ngoài giới hạn), 1 leader/đàn.
- Hard cap `schools`, `members`, `decisions/tick`, `neighborQueries/tick`, `environmentSamples/tick`.
- Chọn đàn mới từ `FishCatalogAdapter`, không phát sinh cá không hợp lệ với EMF requirements.
- Không tạo shared school chứa PRIVATE fish; PRIVATE encounter có school riêng cho owner.
- Đàn biến mất theo population TTL/reservation; cleanup đi qua PacketVisibilityService hiện có.

---

## 5. ENVIRONMENT & WATER FX

### 5.1. Environmental snapshot — read-only

- `EnvironmentSnapshot`: zoneId, water depth band, biome tag, time bucket, weather bucket, current direction (nếu xác định được), surface condition.
- Lấy snapshot với budget/TTL, chỉ ở loaded chunks trên server owner thread; có cache theo chunk/zone.
- Biome/weather/time chỉ tác động đến movement/visual/encounter *nếu fish catalog yêu cầu*, không bỏ qua EMF eligibility.
- Tránh `Block#getRelative` hoặc scan block cho từng agent mỗi tick; tận dụng `WaterIndexService`.
- Weather transition dùng periodic coarse sampling, không lặp toàn player.
- Vùng nước mỏng, kelp/coral/flowing water có rules an toàn; không giả sử toàn bộ cell là nước yên.

### 5.2. Surface FX presets

| FX | Thời điểm | Kỹ thuật | LOD |
|---|---|---|---|
| Subtle Ripple | Cá tới gần mặt nước | 6–12 particle positions vòng nhỏ, hạn chế tốc độ phát | Nhìn gần |
| Bubble Trail | Cá `APPROACHING` | Particle tại các vị trí được sample theo interval, không theo từng pixel | Near |
| Bait Lure Pulse | Phao hợp lệ vừa ổn định | Ring nhẹ phát vài lần, chỉ viewer câu | Owner only |
| Fish Disturbance | Cá hiếm đi dưới mặt nước | Swirl/particle fade bằng curve, không block update | Owner/allowed viewers |
| School Scatter Splash | Đàn hoảng sợ | 1–2 cue theo group, không nhân lên mỗi cá | Near, sampled |
| Legendary Vortex | Encounter/cinematic được duyệt | Vòng particle giảm/tăng bán kính, capped | Owner, optional group |

**Không tạo physics thật:** Không thay nước thành air, không lấy/set block, không đẩy người chơi bằng velocity, không gây damage, không mở chunk và không lưu particle vào DB.

### 5.3. FX scheduling

- Chỉ phát trên **state transition** hoặc các keyframe cách nhau có chủ đích.
- `FxEmissionBudget` giới hạn packet particle/sound riêng; `VisualBudgetCoordinator` phối hợp với ambient packet scheduler.
- `PacketVisibilityService` vẫn ưu tiên destroy; FX là low-priority, có thể drop trước movement của mục tiêu đang câu.
- Tất cả world-space FX cull theo distance/viewer, locale text và privacy; nếu không có viewer hợp lệ thì không emit.
- FX output không được block `FishAiDirector` hoặc lỗi `CatchCommit`.
- Graceful degrade: high FX → fewer particles → no ambience → retain bite signal.

---

## 6. ENCOUNTER SYSTEM — rare presence, shadow, golden, school events

### 6.1. Các loại encounter đề xuất

1. `SINGLE_RARE`: một cá EMF Rare/Epic hợp lệ xuất hiện với bubble/ambient light riêng.
2. `GOLDEN_APPEARANCE`: cá ánh vàng bằng model/effect (fish identity vẫn từ catalog).
3. `SHADOW_FISH`: bóng cá ở tầng sâu, bị che một phần cho đến khi tiếp cận, không fake loot.
4. `GHOST_WATER`: trail mờ về đêm; cần resource pack chỉ để tăng thẩm mỹ, không bắt buộc.
5. `RARE_SCHOOL`: 2–4 cá trong nhóm có vẻ ngoài đặc biệt, vẫn tính theo toàn bộ population budget.
6. `STORM_CHASER`: hiệu ứng theo thời tiết nếu zone/biome và fish eligibility cho phép.

**Chặn thiết kế sai:** Không thể đưa cho player “Legendary encounter” nhưng thực tế target lại là Common. Nếu encounter hiển thị tên rarity, server đã phải gắn với FishKey hợp lệ và khả năng catch thật theo quy tắc.

### 6.2. Encounter lifecycle

`DORMANT → ELIGIBLE → SPAWNED → OBSERVED → RESERVED/IGNORED → COMPLETED/EXPIRED → CLEANUP`.

- `ELIGIBLE`: pass EMF catalog requirement, world/zone/water conditions, per-zone/time cooldown.
- `SPAWNED`: lấy slot từ population, không vượt cap; reservation vẫn do FishPopulationService.
- `OBSERVED`: viewer-specific visibility, policy reveal không leak PRIVATE owner.
- `RESERVED`: chuyển sang FishAiDirector làm mục tiêu, không tạo thuật toán bắt cá thứ hai.
- `COMPLETED`: chỉ thành công khi `SimulatorCatchService` đã commit; visual failure không sửa outcome.
- `EXPIRED`: cá đi khỏi vùng hoặc event hết hạn không cho reward.

### 6.3. Fairness / anti-abuse

- Encounter không tạo additional reward item/EXP ngoài EMF; mọi bonus tương lai phải là config riêng được duyệt và có idempotency.
- Cooldown theo zone, viewer, FishKey; tránh đứng AFK/cast spam để force-spawn legendary.
- Shared encounter chiếm slot thực trong shared population, tránh nhiều viewer thấy ảo không đồng nhất.
- Fish rare có thể reveal *trước khi cắn*; mặc định không có chỉ dẫn lừa player rằng đã cắn.
- Tránh starvation: high-priority encounter không cướp toàn bộ ngân sách packet của các cá thường.
- Staff test command không ảnh hưởng ranking/competition/reward.

---

## 7. PREMIUM CINEMATIC DIRECTOR — yêu cầu chất lượng cao

### 7.1. Điểm khác biệt với Phase 8

Hiện Phase 8/9.1 dùng `CatchArc` với **một chuyển động từ phao về phía player**. Premium director sẽ sử dụng **scene timeline theo keyframe** được điều phối, bao gồm:

- `sceneId`, `viewer`, `hookId`, `fishKey`, `rarity`, `model`, `originSnapshot`, `targetSnapshot`, `startTick`, `phase`.
- `storyboard` chứa các stage: `WATER_ANTICIPATION`, `SURFACE_BREAK`, `FISH_LEAP`, `REVEAL`, `SETTLE`, `CLEANUP`.
- FX tracks độc lập: `MotionTrack`, `SoundTrack`, `ParticleTrack`, `TextTrack`; playback vô hại khi track lỗi.
- Đồng bộ dựa trên tick, không phụ thuộc FPS client; không gọi scheduler riêng cho từng frame/player.
- Scene effect **khởi chạy sau commit thành công**, chỉ dùng immutable snapshot; không tạo/đổi fish reward, không trừ bait.
- `PacketCatchCinematicService` được tái sử dụng ở tầng renderer hoặc refactor thành một service, không phát sinh 2 cinematic owners.

### 7.2. Rarity storyboard — cấu hình dự kiến (không phải code hiện tại)

| Rarity tier | Nhịp cinematic | Thời lượng tham chiếu | Đặc trưng |
|---|---|---:|---|
| Common | Splash → arc ngắn → vanish | 12–16 ticks | Nhẹ và nhanh, ít can thiệp |
| Uncommon | Bubble/swish → short leap | 16–22 ticks | Một cue tăng cảm giác trúng cá |
| Rare | Ripple → cá nhảy → glow highlight → reward text | 24–32 ticks | Đủ nổi bật nhưng không gián đoạn |
| Epic | Water swirl → fish leap + spin → short slow-feel → reveal | 36–50 ticks | Âm thanh 2 lớp, particle có choreography |
| Legendary | Water calm/charge → vortex → dramatic leap → fish orbit/pose → rarity reveal + sound sting → fade | 55–75 ticks | Multi-stage premium, vẫn cho skip |
| Mythical / đặc biệt | Override hoàn toàn theo FishKey (trong hard max) | 65–90 ticks | Cá riêng có animation đặc trưng |

Các mốc tick là **default proposed**, cần tuning sau playtest. Rarity ID thật từ EMF có thể map sang preset; không được hardcode `Mythical` như chắc chắn tồn tại trong mọi server.

### 7.3. Legendary timeline mẫu (70 ticks)

| Ticks | Stage | Hình ảnh | Âm thanh/UX |
|---|---|---|---|
| 0–8 | `ANTICIPATION` | Mặt nước yên + ring thu nhỏ, particle ít | Âm trầm nhẹ (private) |
| 9–19 | `WATER_VORTEX` | Vòng xoáy tăng tốc tại phao, bubble line | Riser tăng pitch nhưng không chói |
| 20–34 | `BREAK_SURFACE` | Virtual fish vượt mặt nước, hướng quay bám tangent | Splash chính, không spam HUD |
| 35–48 | `HERO_REVEAL` | Cá xoay nhẹ, giữ gần field-of-view nhưng không che màn hình; rarity color trail | Signature chime + text ngắn |
| 49–61 | `DELIVERY_MOMENT` | Fish arc giảm tốc về gần player, một glint/halo | Reward confirmed cue |
| 62–70 | `FADE_CLEANUP` | Giảm particle, destroy fish ID, dọn anchor | Không thêm sound lớn |

**Không “đóng băng thời gian” thật:** slow-motion cảm giác được tạo bằng interpolation/easing của cá ảo, không giảm TPS, không freeze player và không thay đổi physics game.

### 7.4. Professional animation rules

- Đường bay `Bezier/Catmull-Rom` với ease-in/out và soft banking theo velocity, có clamp yaw/pitch, không giật khi viewpoint thay đổi.
- Origin lấy snapshot tại thời điểm câu; end gần góc nhìn người câu **nhưng không đè camera/mũi cần**.
- Dùng local offset/anchor snapshot, có fallback khi người chơi di chuyển nhanh.
- Không spawn cá thật, không chỉnh camera/player transform; `camera shake` nếu có chỉ được mô phỏng rất nhẹ qua visual/sound và phải optional.
- Scene chỉ render cho player có quyền xem, giữ viewer-local virtual entity ID + cleanup queue Phase 9.1.
- Destroy/skip/quit/world change/reload/dependency unload không thể tạo reward hay giữ ghost fish.
- Nếu packet budget cạn, scene có thể hạ chất lượng hoặc tắt animation, không kéo dài vô hạn.
- Nếu commit đã thành công nhưng visual thất bại: người chơi vẫn giữ item/stats; không tạo retry reward.

### 7.5. Scene choreography và sound

- Mỗi stage có **một** cue chính + vài cue phụ giới hạn; không play tất cả FX/sounds cùng tick.
- `BITE_READY` dùng cue nhận diện riêng, khác `LEGENDARY_REVEAL`.
- Sound spatial/private, không toàn server; optionally nearby announcement nếu config cho phép và rarity cao.
- Pitch/volume/mix được validate, profile chung/bearable; tránh blast quá lớn khi câu liên tiếp.
- Reward reveal có tên fish, size và rarity lấy từ **catch snapshot sau commit**, không tự gọi lại FishManager để roll.
- Bảo vệ text animation: max length, MiniMessage trust boundary, fallback locale.

### 7.6. Custom cinematic overrides

- Override theo `rarityId:fishId`, zone/tag tùy chọn.
- Override chỉ thay hình/âm, không đổi drop và requirement.
- Cho phép `model`, `trail`, `stage duration`, `arc`, `surface ring`, `sound`, `title`, `viewer policy`, `quality fallback`.
- Mỗi override cần validate tổng duration/particle/packet, loop count, keyframe order.
- Không cho raw admin command/callback tùy ý trong cinematic config; tránh reward command injection.

---

## 8. CUSTOM MODEL / RESOURCE PACK — optional nhưng hoàn chỉnh

- Tiếp tục dùng vanilla packet fish (`COD/SALMON/TROPICAL_FISH/PUFFERFISH`) nếu không có pack.
- Viết `ItemDisplayFishRenderer` riêng tại adapter Paper 1.21, không sửa core `FishKey`/`FishAgent` contract.
- Model registry dùng `minecraft:item_model` hoặc custom model data theo target API/pack, kiểm tra item-model support đúng client/version.
- `FishVisualModel` được mở bằng strategy `VANILLA_MOB`, `ITEM_DISPLAY` và fallback `VANILLA_MOB`; không bỏ model đang hiển thị khi metadata unsupported.
- Với display entity ảo: định nghĩa size, transform, rotation, visibility, interpolation, brightness, billboard policy, glint; **phải test từng wrapper và metadata bằng client thật**.
- Không tự động render cùng lúc vanilla fish + ItemDisplay cho cùng agent.
- Người chơi không có pack hoặc pack fail: fallback vanilla và không leak invisible body/ghost entity.
- Hỗ trợ custom fish cinematic riêng nhưng giới hạn transform và animation length.
- Có compatibility matrix Paper 1.21.4, phiên bản thực tế của server, PacketEvents 2.14.0, client Java có/không có pack.

---

## 9. 🧱 Technical architecture

### 9.1. Reuse, don't rewrite

```text
com.oheers.fish.fishing.simulator
├── SimulatorModule.java                # existing orchestration, KEEP THIN
├── AmbientPreviewService.java          # existing population renderer owner
├── ai/
│   ├── FishAiDirector.java             # existing authoritative bite/reel
│   ├── FishAttractionPolicy.java        # existing bait/rod policy
│   └── behavior/                      # NEW personalities, transition signals
│       ├── FishBehaviorProfile.java
│       └── BehaviorProfileRegistry.java
├── population/
│   ├── FishAgent.java                  # existing, extend minimally
│   ├── FishPopulationService.java      # existing population ownership
│   └── school/                        # NEW
│       ├── FishSchool.java
│       ├── FishSchoolService.java
│       ├── SchoolSteering.java
│       └── SchoolSpatialIndex.java
├── environment/                        # NEW
│   ├── EnvironmentSnapshot.java
│   ├── EnvironmentSampler.java
│   └── EnvironmentRuleService.java
├── encounter/                          # NEW
│   ├── EncounterDefinition.java
│   ├── EncounterDirector.java
│   └── EncounterCooldownStore.java
├── presentation/                       # NEW
│   ├── PresentationArbiter.java
│   ├── UiIntent.java
│   ├── FishingHudRenderer.java
│   ├── PlayerVisualPreferences.java
│   └── VisualPreferenceService.java
├── effects/
│   ├── SimulatorEffectsService.java    # existing → bridge to new director
│   ├── CatchArc.java                   # existing → short/fallback arc
│   ├── PacketCatchCinematicService.java # existing → single owner/renderer
│   ├── SurfaceFxService.java           # NEW
│   └── cinematic/                     # NEW
│       ├── CinematicDirector.java
│       ├── SceneTimeline.java
│       ├── SceneInstance.java
│       ├── SceneTrack.java
│       └── ScenePresetRegistry.java
├── visual/
│   ├── PacketTransport.java            # existing abstraction
│   ├── PacketVisibilityService.java    # existing destroy-first/LOD extension
│   ├── PacketCleanupQueue.java         # existing shared cleanup logic
│   ├── VisualBudgetCoordinator.java    # NEW
│   └── FishVisualModelRegistry.java    # NEW
├── commit/
│   └── SimulatorCatchService.java      # existing, NO cinematic mutation
├── config/
│   └── ... validated snapshots/loaders  # NEW typed schema adapters
└── hooks/
    └── ... existing provider adapters  # keep optional

Paper 1.21 adapter (existing project module)
└── PacketEvents transport / VANILLA_MOB / optional ITEM_DISPLAY metadata
```

Các đường class mới ở trên là **đề xuất**, chưa có trong source Phase 9.1. Module phụ thuộc một chiều: **AI/Population → semantic events → Presentation/Visual**; AI không import GUI/Particle classes, Presentation không import commit/service mutators.

### 9.2. State + event contract

`FishingPresentationEvent` (immutable) có `eventId`, `sessionId`, `playerId`, `fishAgentId`, `FishKey`, `phase`, `timestampTick`, `zoneId`, `positionSnapshot`, `catchSnapshot?`.

- Event producer `FishAiDirector` chỉ nêu sự thật gameplay: detected, approach, bite, reel success/fail.
- School/Encounter phát semantic effect intents; nếu fish không hợp lệ/không visible thì director drop.
- `CinematicDirector` chỉ chấp nhận `CatchCommitSuccess` hoặc admin preview token, không chấp nhận packet/client tự tạo.
- `PresentationArbiter` giữ state theo UUID và session generation; intent cũ không ghi đè session mới.
- Scene có `sceneId`, và mọi delayed tick phải check `sceneId == active[uuid].id` trước khi apply.
- Dùng immutable snapshot qua đường async; Bukkit/Paper/PacketEvents owner operations chỉ trên Paper thread được xác định.

### 9.3. Threading matrix

| Tác vụ | Thread model | Ghi chú |
|---|---|---|
| PlayerFishEvent, FishHook, world/chunk, sound/particles, inventory, packet sender if required | Paper main/owner thread | Không gọi từ worker thread |
| School motion từ snapshot thuần data | Main với per-tick budget hoặc async snapshot computation | Apply kết quả có revision guard trên main |
| Environment sampling world/biome/block | Paper owner thread | Không quét block từ async |
| Parse YAML/migration, preference DB operations | Worker async, apply validated snapshot sync | Không block main |
| Animation sampling bezier, curve math nhỏ | Main trong batch budget | Không task/player |
| Placeholder / locale read | Cached snapshot | Không query DB sync trong UI |
| Shutdown | Cancel → flush bounded → destroy pending → close | Không mất session ownership |

**Không Folia claim.** Nếu về sau muốn hỗ trợ Folia, cần thiết kế region/entity scheduler và cross-region fish ownership từ đầu, không chỉ thay task scheduler.

---

## 10. CONFIG ARCHITECTURE + VALIDATION

### 10.1. Giữ backward compatibility

- `config.yml` gốc EMF vẫn dùng key migration `version`; **không đổi sang `config-version` tùy tiện**.
- `simulator.yml` đang có `config-version: 1`; feature flags hiện có giữ nguyên: `simulator.enabled`, `ambient-preview`, `ai-preview`, `native-hook-probe`, `reward-bridge`.
- Giữ `simulator/effects.yml` và `simulator/visuals.yml`; migrate bổ sung, không xóa 4 preset `common/rare/epic/legendary` cũ.
- Thêm tối đa **3 file** chuyên trách để tránh `simulator.yml` quá lớn:
  - `simulator/presentation.yml`: HUD channels, player preferences defaults, accessibility.
  - `simulator/ecosystem.yml`: school, environment, personality, rare encounter, water FX.
  - `simulator/cinematics.yml`: scene timeline, rarity profile, FishKey override.
- Với custom models, mở rộng `simulator/visuals.yml`, không tạo thêm file riêng.
- Mọi file có `config-version` riêng; migration giữ comment/config người dùng khi an toàn.
- File thiếu → bổ sung default; key sai → log path/expected/range và fallback an toàn hoặc disable feature; **không silently enable**.

### 10.2. `simulator/presentation.yml` — **proposed example**

```yaml
config-version: 1
presentation:
  enabled: false            # stays OFF until Paper runtime gates pass
  layout: BALANCED          # OFF, MINIMAL, BALANCED, CINEMATIC, REDUCED_MOTION
  arbitration:
    one-message-per-channel: true
    bite-preempts-ambient: true
    stale-intent-expire-ticks: 20
  actionbar:
    enabled: true
    interval-ticks: 5
    max-length: 96
    show-bite-timer: true
  near-bobber:
    enabled: true
    use-virtual-text: false  # fallback particles/sound; test packet text first
    distance: 20
  bossbar:
    enabled: false
  titles:
    enabled: true
    only-rare-and-above: true
    cooldown-ticks: 60
  player-preferences:
    enabled: true
    default-quality: BALANCED
    save-debounce-seconds: 5
```

### 10.3. `simulator/ecosystem.yml` — **proposed example**

```yaml
config-version: 1
ecosystem:
  enabled: false
  schools:
    enabled: true
    max-per-zone: 6
    max-members-per-school: 5
    steering-interval-ticks: 4
    max-neighbor-queries-per-tick: 160
    scatter-radius: 4.0
    regroup-delay-ticks: 30
  environment:
    enabled: true
    sample-interval-ticks: 80
    max-samples-per-tick: 12
    cache-ttl-ticks: 200
    allowed-depth-bands: [SHALLOW, MID, DEEP]
  personalities:
    enabled: true
    default: CALM
    fish-overrides: {}
  encounters:
    enabled: false
    max-active-per-zone: 1
    spawn-cooldown-seconds: 240
    per-player-cooldown-seconds: 120
    preview-only: true
  water-fx:
    enabled: false
    max-emissions-per-tick: 24
    player-view-distance: 24
    common-ambient-chance: 0.15
```

### 10.4. `simulator/cinematics.yml` — **proposed example**

```yaml
config-version: 1
cinematics:
  enabled: false
  preview-only: true
  max-active-global: 4
  max-active-per-viewer: 1
  max-duration-ticks: 90
  max-packets-per-tick: 12
  skip-on-new-cast: true
  cancel-on-world-change: true
  camera-control: NONE     # Never force rotation/teleport
  accessibility:
    honor-reduced-motion: true
    no-screen-flash: true
  rarity-profiles:
    Common: common_short
    Uncommon: uncommon_short
    Rare: rare_reveal
    Epic: epic_spiral
    Legendary: legendary_hero
    Mythical: mythical_hero
  fish-overrides: {}
profiles:
  legendary_hero:
    enabled: true
    total-duration-ticks: 70
    stages:
      anticipation: {from: 0, to: 8, fx: subtle_ring}
      vortex: {from: 9, to: 19, fx: golden_vortex}
      leap: {from: 20, to: 34, motion: dramatic_leap}
      reveal: {from: 35, to: 48, motion: hero_pose, title: reveal_legendary}
      delivery: {from: 49, to: 61, motion: arc_to_player}
      cleanup: {from: 62, to: 70, fx: fade_out}
```

**Lưu ý:** Các YAML trên là bản thiết kế schema mới, chưa được loader hiện tại hỗ trợ. Không copy vào server đang chạy Phase 9.1 và kỳ vọng chúng hoạt động ngay.

### 10.5. Validators bắt buộc

- `world/region`, `model/material/item-model`, particle/sound, locale keys, permission, schema version.
- `rarityId:fishId` tra catalog; missing fish mapping fallback an toàn có warning, không tạo phantom rewards.
- Số, integer ranges, max-per-zone/viewer, duration, FPS/update interval, spline bounds, volume/pitch.
- Stage order không overlap trái phép, stage time không vượt scene total duration/hard max.
- Disabled feature không vô tình chạy do file khác `enabled: true`.
- Cross-config dependencies: cinematic requires a valid renderer + packet transport; encounter requires population; public reward requires Paper gates.
- `reload` dùng two-phase snapshot: parse **tất cả** candidates → validate/dependency check → atomically swap → invalidate/generation guard → cleanup old scenes; lỗi bất kỳ thì giữ snapshot cũ.

---

## 11. COMMANDS, PERMISSIONS, GUI, STORAGE

### 11.1. Planned commands (giữ cây `/emf admin simulator`)

| Command | Permission dự kiến | Hành động |
|---|---|---|
| `/emf admin simulator preview <rarity> [fishKey]` | `emf.admin.simulator.preview` | Một scene visual-only cho admin |
| `/emf admin simulator fx test <fx-id>` | `emf.admin.simulator.preview` | Kiểm tra FX riêng mình |
| `/emf admin simulator cinematic stop` | `emf.admin.simulator.preview` | Dừng scene admin |
| `/emf admin simulator school preview <preset>` | `emf.admin.simulator.preview` | Preview một đàn cá TTL ngắn |
| `/emf admin simulator encounter preview <id>` | `emf.admin.simulator.preview` | Preview rare encounter visual-only |
| `/emf admin simulator doctor` | `emf.admin.simulator.status` | Mở rộng health/metrics |
| `/emf simulator settings` | `emf.simulator.settings` | Player settings GUI; kiểm tra trùng command với EMF trước khi đăng ký |

- Node mới phải khai báo permission trong plugin.yml hoặc registry hiện có với default an toàn.
- Admin commands cần usage/help, tab complete, player-only guard, cooldown, validation fish key/rarity.
- Console chỉ được gọi status/doctor/config, không thể mở scene theo mình.
- Preview tuyệt đối không gọi reward, competition, `EMFFishCaughtEvent` hoặc lưu history.

### 11.2. Player preferences GUI

`Fishing Settings` — GUI 27/36 slot (config layout), button các nhóm: HUD, âm thanh, particle, rare alert, cinematic, preset quality, accessibility, preview và reset.

- Custom `InventoryHolder`, `GuiSession` theo UUID, button/action IDs, không kiểm tra title/displayName để xác định action.
- Cancel click mặc định; kiểm tra `rawSlot` top/bottom; chặn shift/double/number-key/hotbar swap/collect-to-cursor, drag top inventory.
- Disable controls nếu server config không cho; GUI không mở tùy tiện khi reload/session stale.
- Open/render/update inventory trên Paper main thread; storage async; UI cập nhật bằng snapshot/version guard.
- Text lấy locale/config; icon material/slot/glow/sound định nghĩa trong config có validator.
- Ưu tiên thêm entry vào GUI EMF sẵn có thay vì tạo một hệ menu độc lập không cần thiết.

### 11.3. Storage & compatibility

- Cosmetics không cần ghi theo từng frame/FX; chỉ lưu **preference user**, không lưu fish schools/animation session.
- Tái sử dụng repository/storage hiện có của EMF khi có khả năng; nếu không phù hợp, thêm namespace riêng `simulator_preferences` trong SQLite, không đổi bảng fish history.
- Cột tối thiểu: uuid (primary key), preset, flags, updated_at, schema_version; migration reversible.
- DB I/O async, prepared statements, WAL/busy timeout nếu dùng SQLite; debounce update, bounded cache UUID, flush có timeout khi disable.
- Fail to read prefs → default từ config, không chặn player join/fishing.
- Network mode nếu EMF dùng MySQL thì adapter phải có cùng storage policy, không âm thầm tạo SQLite gây settings khác nhau giữa server.

---

## 12. PERFORMANCE SPEC & DEGRADATION POLICY

### 12.1. Ngân sách baseline hiện có

- `ambient-preview.max-agents: 160`.
- `ambient-preview.max-per-zone: 48`.
- `ambient-preview.max-visible-per-viewer: 20`.
- `ambient-preview.packets-per-tick: 800`.
- `ai-preview.max-active: 8` và bounded search.
- `cinematic.max-concurrent: 4`, `packet-budget-per-tick: 12`.

**Không tự tăng các giới hạn này** chỉ vì thêm feature. Các ngưỡng có thể được tune sau profiler và client tests.

### 12.2. LOD policy đề xuất

| Distance từ viewer đến fish | Update motion | Effects | Visual model |
|---|---:|---|---|
| 0–12 block | Mỗi 2 tick nếu có budget | Full chỉ khi relevant | Normal/high |
| 12–24 block | Mỗi 5 tick | Sparse trail/ripple | Normal |
| 24–40 block | Mỗi 10–20 tick | OFF trừ rare shared indicator đã được cho phép | Simplified |
| >40 block | Không spawn/ẩn | OFF | Không giữ client virtual entity |

Cá đang được target/đang cắn được ưu tiên update so với ambient school motion, nhưng không bao giờ vượt hard budget và cleanup.

### 12.3. Budget coordinator

- `DESTROY / CLEANUP RETRY` > `ACTIVE HOOK TARGET` > `CINEMATIC CONTROL` > `SPAWN` > `MOVEMENT` > `AMBIENT FX`.
- Hiện ambient packet và cinematic có budget riêng: thêm một **hard global safety ceiling** để tránh vượt tổng mong muốn.
- Particle Bukkit/Paper cũng tạo network traffic; thống kê riêng `particle-emissions` và ước lượng packet, không coi chỉ số PacketTransport là tổng network thực tế.
- Một viewer tối đa một cinematic; hiệu ứng chỉ cho người có quyền xem; FX không có queue unbounded.
- Nếu backlog quá lớn, giảm quality: turn off water ambience → rare encounter background → school decorative steering → cinematic quality, **giữ được bite signal**.
- Adaptive quality dùng EWMA trên processing time và packet failures, có hysteresis; không toggle liên tục mỗi tick.
- Bất kỳ degrade nào cũng **không thay đổi fish rarity, loot, quyền target/reel hoặc EMF stats**.

### 12.4. Performance test targets (đề xuất, phải đo thật)

- Nghiệm thu ở staging Paper và phần cứng được ghi rõ, không dùng mock TPS như benchmark.
- Test 1, 10, 30, 50, 100 người trong zone/đang câu; ghi server version, plugin hooks, network latency và hardware.
- Mục tiêu khởi điểm để điều chỉnh: sau warm-up, plugin-specific p95 tick processing dưới 1 ms tại 30 anglers, p99 không tăng đột biến không kiểm soát; đây là **target chứ không phải cam kết**.
- Tối thiểu quan sát: MSPT p50/p95/p99, TPS, heap allocations, GC spikes, outbound packet samples, per-viewer spawned fish, cleanup backlog, animation queue, packet failures.
- Peak load vẫn không có ghost fish sau 30s cleanup grace hoặc player reconnect nếu transport bị sự cố không phục hồi.

---

## 13. RELIABILITY / COMPATIBILITY / SECURITY

### 13.1. Gate G3 — Native FishHook

- Kiểm chứng Paper thật việc suppress native bite/catch/XP, event ordering và interaction WorldGuard/RedProtect.
- Không thêm catch listener riêng để bypass event cancellation.
- Điều kiện bất khả dụng quyết định trước takeover; owned hook thất bại → fail-closed/cleanup; không reward legacy muộn.

### 13.2. Gate G5 — Packet renderer

- Client thực thấy shared/private fish đúng; origin, yaw, movement, despawn, chunk unload, world change.
- Partial spawn failure, metadata failure, destroy failure, reload, PacketEvents disable/restart, disconnect/reconnect; `PacketCleanupQueue` phải hoạt động.
- ITEM_DISPLAY metadata cần matrix riêng; thất bại thì fallback vanilla.

### 13.3. Gate G7 — Catch/Reward consistency

- Event canceled → không giao item, không trừ bait, không ghi stats.
- Commit success → đúng fish metadata + size + rarity + EMF event/competition một lần.
- Concurrent reel, number key/inventory full, drop fail, plugin disabled mid-commit, player quit/reconnect.
- Cinematic failure không bao giờ retry item; preview không bao giờ cấp item.
- Native loot/bait loot/treasure fallback hiện tại cần được giữ đến khi có policy tương đương được kiểm thử, không tự bỏ vì aesthetic.

### 13.4. Cinematic failure recovery

- Scene cancel trên quit/world unload/zone invalid/new cast/reload/dependency failure.
- Mọi virtual entity phải có destroy ledger; partial spawn phải được track even if API says failure.
- Không retry FX vô hạn; timeout rõ, retry cleanup có ưu tiên cao.
- Audio/particle exception được cô lập, không dừng global owner ticker nếu không phải lỗi nghiêm trọng.
- Scene priority tránh nhiều cinematic do double catch/competition/broadcast xếp cùng lúc; **visual có thể merge nhưng không merge reward**.

### 13.5. Security

- Player không điều khiển FishKey/rarity/scene via packets. Server giữ state/UUID/generation.
- Command inputs có length/whitelist, permission, rate limit; fish IDs lấy từ catalog.
- Text user-controlled được escape, không parse trusted MiniMessage.
- Không giữ Player object trong cache dài hạn, không broadcast private fish ID.
- Không log password/token hoặc per-frame spam.

---

## 14. PHASED IMPLEMENTATION ROADMAP (tiếp nối Phase 9.1)

> **Không triển khai tính năng UI mới nếu baseline chưa compile.** Các mốc dưới đây là backlog; chỉ code khi người dùng đồng ý bước triển khai.

### PHASE 9.2 — Build/Staging foundation (**BLOCKER P0**)

**Mục tiêu:** Full compile project, chạy JUnit thật, sửa incompatibility Paper/PacketEvents, xác nhận G3/G5/G7.

- Khôi phục Gradle 9.5.1 (hoặc project-compatible wrapper) qua môi trường có mạng/cache.
- `./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon` trên baseline.
- Kiểm tra plugin descriptor, runtime dependencies và PacketEvents bridge.
- Setup Paper staging với build/version đã chốt, record screen + timings và reproduction notes.
- Chặn public Simulator reward nếu chưa pass; không “paper-gates-verified: true” chỉ để cho chạy.
- **Exit:** full compile PASS + tests PASS + G3/G5/G7 có bằng chứng từ Paper thật. Nếu không pass, đánh dấu BLOCKED và sửa trước.

### PHASE 10 — HUD/Presentation Quality (**P1**) 

**Mục tiêu:** Sửa vấn đề chữ chồng nhau/khó đọc thể hiện trong screenshot.

- Thiết kế `PresentationArbiter`, UI priorities, channel ownership, dedupe/throttle.
- Nâng bite HUD thành responsive, legible, locale safe; không phát title cho `INTERESTED` thường.
- Player settings GUI + preference persistence với các preset OFF/MINIMAL/BALANCED/CINEMATIC/REDUCED_MOTION.
- Tạo tooltip/sound state-based, debug HUD intents.
- **Exit:** không overlay text trong test matrix UI/HD/different plugins, settings survive relog, GUI anti-exploit PASS, no main-thread DB.

### PHASE 11 — Cinematic Director V2 (**P1, ưu tiên cao**)

**Mục tiêu:** Cá Rare/Epic/Legendary được câu trông chuyên nghiệp, có sequence được đạo diễn.

- `CinematicDirector`, timeline/keyframe, stage tracks, easing/orientation, scene ownership/skip.
- Rarity profiles, per-FishKey cinematic override; render vanilla trước, resource pack optional.
- Scene snapshot sau commit, cleanup/safe fallback, sound choreography.
- Premium Legendary ~70 ticks, viewer-only; toggle/reduced-motion.
- **Exit:** reveal của Common/Rare/Epic/Legendary khác nhau có chủ đích, không giật/hide bug, không double reward, cinematic stop/reload cleanup PASS.

### PHASE 12 — Fish Schools & Natural Movement (**P1**)

**Mục tiêu:** Cá bơi thành đàn thật sự thay vì chuyển hướng đơn lẻ.

- School model, leader/follower, spatial hash, formation, scatter/rejoin, obstacle avoidance.
- Bảo toàn reservation/AI authority; shared/private viewer policy.
- Batch steering + LOD; không tăng agents vượt existing caps.
- **Exit:** school trajectories mượt ở client, một cá đang target không bị kéo về đàn, stable budget trong synthetic và Paper test.

### PHASE 13 — Environment & Water Presence (**P2**)

**Mục tiêu:** Hồ cảm giác sống động, có phản ứng theo môi trường.

- `EnvironmentSampler` cache theo loaded chunks; personality CALM/CURIOUS/SHY/... trong bound.
- Ripple, bubble trail, lure pulse, disturbance; water FX chỉ cosmetic.
- Biome/depth/time/weather mapping, không bypass EMF eligibility.
- **Exit:** no forced chunk load; môi trường bật/tắt riêng, phong cách hợp lý, không flood packet.

### PHASE 14 — Rare Encounter Director (**P2**)

**Mục tiêu:** Rare presence và đặc biệt trong hồ tạo khoảnh khắc đáng nhớ.

- Encounter lifecycle, weighted conditions, cooldown, share/private, observation/reveal.
- GOLDEN/SHADOW/GHOST/SCHOOL event, FishKey real; special cue config.
- Admin preview, fail-closed missing catalog/region, spam safeguards.
- **Exit:** rare encounter không gây phantom reward/loot weight exploit, không leak private encounter, expired cleanup đầy đủ.

### PHASE 15 — Custom Model & Resource Pack (**P2 Optional**)

**Mục tiêu:** `ItemDisplay` packet custom fish chạy thật và có fallback.

- Version-specific PacketEvents item display metadata, model registry, transform/rotation.
- Pack-present/absent behavior, pack download failure fallback, compatibility matrix.
- Cinematic per-model transforms và orientation.
- **Exit:** không invis fish, không ghost display/duplicate, vanilla fallback tất cả client.

### PHASE 16 — Optimization, QA, Rollout & Release Candidate (**P0**)

**Mục tiêu:** Chốt chất lượng thực tế thay vì chỉ parser/smoke.

- JUnit + Paper integration + client recording + profiler.
- Stress test 1/10/30/50/100 anglers, zone boundaries, packet failure injection, hot reload, shutdown.
- Verify full `FishKey`/event/competition/stats/DB compatibility, old EMF regression.
- Opt-out, accessibility, locale, warnings, config migration/backups, rollback docs.
- Rollout: admin preview → canary permissions/zone → public zone, không bật tất cả cùng lúc.
- **Exit:** build+tests+runtime gates pass, không unresolved P0/P1, có benchmark/rollback evidence và server owner sign-off.

### 14.1. Dependency graph

```text
P9.2 Build + G3/G5/G7
          |
          v
P10 HUD/UI ───────> P11 Cinematic Director V2
          |                    |
          v                    v
P12 Fish Schools ─> P13 Environment & Water FX
          |                    |
          +──────────────> P14 Rare Encounter
                               |
                               v
                       P15 Custom Model (optional)
                               |
                               v
                     P16 QA / Production RC
```

**Có thể làm P10/P11 trên source staging để cải thiện UX**, nhưng *không* đánh dấu production hoặc mở reward công khai nếu P9.2 chưa vượt gates.

---

## 15. TEST MATRIX — bắt buộc có evidence

### 15.1. Gameplay regression

- G-01: simulator disabled → EMF vanilla như trước.
- G-02: ngoài zone → legacy; trong zone khi engine unavailable → policy đúng.
- G-03: nhiều rods / offhand / bait, cancel/no-permission, early reel, missed window.
- G-04: fish shared/private; visual = FishKey reward.
- G-05: custom rod bonus, double catch, bait consume, competition/journal/history một lần.
- G-06: inventory full/drop fail; no duplicate/dangling state.
- G-07: rare encounter không ảnh hưởng loot weight trái config.

### 15.2. Cinematic/UX

- U-01: `INTERESTED` và `BITE_READY` không hiển thị hai thông báo chồng nhau.
- U-02: ActionBar HUD không còn sau hụt/câu xong/quit/reload.
- U-03: Legendary cinematic đúng fish/rarity, duration/cues đúng preset; camera không bị đổi góc.
- U-04: Player `OFF`, `REDUCED_MOTION`, `MINIMAL` không nhận scene không mong muốn.
- U-05: Skip, move away, change world, death, quit, reconnect đều dọn virtual entity.
- U-06: FishKey override đúng ưu tiên: fish override > rarity profile > common fallback.
- U-07: Title/subtitle/particle readable trong nước tối, ngày sáng, nhiều người và có resource pack/không pack.
- U-08: GUI shift/double/drag/number key/hotbar swap an toàn.
- U-09: FX fail/exception không hủy commit hợp lệ.
- U-10: cảnh Epic/Legendary không gây quá nhiều sound/titles nếu bắt liên tiếp.

### 15.3. Ecosystem/performance

- P-01: leader/follower route chỉ qua water-index cells, không xuyên đất.
- P-02: school detach khi một agent reserved; failure fish returns/wanders.
- P-03: 100 viewer mock budget, fairness; 30–100 người test Paper dùng profiler.
- P-04: packet retry partial spawn/destroy failures; no duplicate/ghost after recovery.
- P-05: chunk unload, world unload, engine off, region denied, reload invalid config.
- P-06: degrade quality không sửa fish identity/loot/bite window.
- P-07: passive water FX drop trước critical bite packet.
- P-08: không synchronous DB query khi thay preference/hud/catch.
- P-09: persistence toggle survive restart; schema migration giữ dữ liệu.
- P-10: thiếu PacketEvents/custom pack/provider → safe fallback/disable.

### 15.4. Release evidence artifacts

- Build log thật, test report thật, versions/dependency hashes.
- Screenshots/video G5 + cinematic Common/Rare/Epic/Legendary.
- Recorded G3 native catch/XP suppression proof; G7 event/DB consistency proof.
- profiler spark/Timings+MSPT (ghi công cụ sử dụng, điều kiện máy); packet metrics theo viewer.
- Rollback instructions, config migration test và changelog.

---

## 16. Production notes — failure handling & operations

- **Feature rollout:** mỗi subsystem có `enabled`; staged enable sau P9.2, không bật bằng một lệnh `on` toàn bộ khi chưa test.
- **Lifecycle:** disable/reload cắt scene/school/encounters, bảo toàn pending destroys; retry có bounded memory; disconnect có cleanup policy riêng.
- **No goroutine/task per fish:** batch tick từ owner scheduler, bounded work queues.
- **Cache:** school/environment/prefs đều bounded; clear khi zone unload/quit/reload.
- **Reload:** rollback old config khi parse fail, không tắt cả EMF vì một preset particle sai nếu có safe fallback.
- **Observability:** active schools/agents/encounters, per-viewer virtual entity IDs, FX dropped, cinematic canceled/failed, pending destroys, HUD arbitration suppressed, tick duration, fallback reason.
- **Error logging:** log aggregate & throttle; không spam một lỗi packet mỗi tick.
- **Perf guard:** safety mode ngừng ambient/scene trước, vẫn bảo vệ native hook and existing EMF gameplay.
- **External plugins:** WorldGuard/RedProtect/PacketEvents optional provider boundaries; LeDatPlatform optional (không harddepend); không duplicate services nếu không cần.
- **Compatibility:** chỉ tuyên bố bản Paper/version đã test, không claim Folia.

---

## 17. Implementation instructions for Codex / future phases

1. Mở source **Phase 9.1** và file plan này; không code trên ZIP EvenMoreFish gốc.
2. Xác minh full Gradle compile và Paper staging **trước khi sửa gameplay pipeline lớn**.
3. Mỗi phase có: `scope`, `files changed`, `tests added`, `commands/config/messages`, `metrics`, `risk`, `rollback`, `DONE/INCOMPLETE`.
4. Không xóa/đổi key EMF config/event/public API; không copy bytecode/asset RozenFishing.
5. Không đổ mọi logic vào `SimulatorModule`/`SimulatorEffectsService`; dùng service chuyên trách và callback thuần dữ liệu.
6. Không tạo sample config giả làm đã implemented; file schema được thêm ở phase có loader/validator/test tương ứng.
7. UI use MiniMessage/locale fallback; config chứa tất cả text, sound, duration, colors, items, GUI actions.
8. Không public enable, reward-bridge, `paper-gates-verified` khi runtime gates chưa pass.
9. Chạy lại regression EMF vanilla và script smoke Phase 01–9.1 sau từng phase; thêm JUnit/Paper tests, ghi rõ test nào thực thi.
10. Không gửi JAR production nếu thiếu build+integration evidence; nếu blocked, bàn giao source patch/report và blocker cụ thể.

### Proposed incremental release tags

| Tag | Nội dung | Có thể demo |
|---|---|---|
| `premium-preview-ui` | Phase 10 | HUD và player settings trên staging |
| `premium-preview-cinematic` | Phase 11 | Rarity cinematic admin visual-only |
| `premium-preview-ecosystem` | Phase 12–13 | School + water ambience |
| `premium-preview-encounter` | Phase 14 | Rare encounter preview |
| `premium-models` | Phase 15 | Resource pack custom fish |
| `premium-rc1` | Phase 16 | Chỉ khi build, G3/G5/G7 và runtime test PASS |

---

## 18. Definition of Done — Premium Simulator

- [ ] Full Gradle compile, unit tests, Paper runtime G3/G5/G7.
- [ ] Legacy EMF catches, stats, shop, journal, competition, bait/rod unaffected.
- [ ] Không có HUD overlap trong ảnh test, feedback rõ từng state.
- [ ] Player GUI preferences và opt-out hoạt động, settings persist.
- [ ] Cá bơi theo đàn, personality có tác dụng và không vượt ô nước/chunk/zone.
- [ ] Environment sampling budget/cache đúng, không block main thread.
- [ ] Rare encounter dùng cá EMF hợp lệ và giữ viewer privacy.
- [ ] Cinematic Common/Rare/Epic/Legendary có timeline riêng, không spam effects.
- [ ] Legendary cinematic đẹp và ngắn gọn; không force camera/teleport, có skip/reduced-motion.
- [ ] Custom model được test live hoặc fallback vanilla đúng.
- [ ] Không double reward, không phantom fish, không pending entity leak không kiểm soát.
- [ ] Stress/Paper profiler đo thật; giới hạn packet/FX/LOD giữ đúng.
- [ ] Config migration/reload/fallback, locale, permission và admin diagnostics hợp lệ.
- [ ] Tài liệu vận hành, canary rollout, rollback và release notes đầy đủ.

**Kết luận:** Ưu tiên **P9.2 + P10 + P11** để sửa HUD hiện tại và đưa cinematic cá hiếm lên chất lượng premium, rồi làm **P12–P14** để tạo hệ sinh thái cá sống động. Các tính năng được chia module có toggle nhằm tránh lặp lại tình trạng source “nhiều hiệu ứng nhưng rời rạc/khó tối ưu”.

---

## Implementation tracking — Phase 12 (2026-10-11)

- [x] `FishSchool`, `FishSchoolService`, validated `SchoolSettings` and `SchoolConfigLoader`.
- [x] Schooling is strictly ambient-preview visual only, preserves `FishPopulationService` caps and AI reservations.
- [x] New disabled-by-default `simulator/schooling.yml`, atomic reload candidate and 18-locale diagnostic key.
- [x] Java 21 headless tests: 160 fish / 100 viewers, private/shared separation, steering budget, invalid water, scatter/reform, no extra rewards.
- [ ] Full Gradle build and JUnit tests with real Paper/PacketEvents dependencies (Gradle distribution DNS unavailable in execution environment).
- [ ] Real Minecraft client school motion/PacketEvents render test; staging G3/G5/G7.
- [ ] Phase 13 (weather/biome, personality and water presence VFX), Phase 14 (rare encounters), Phase 15 (custom models).

**Status:** Source-ready for further development and staging validation only; not production-ready.
