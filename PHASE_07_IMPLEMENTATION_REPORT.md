# 🎣 EvenMoreFish Simulator V2 — Phase 7 Implementation Report

**Baseline:** `EvenMoreFish-Simulator-Phase06` (cumulative Phase 01–06).  
**Phase:** Simulator ↔ EMF catch & reward bridge.  
**Status:** **Source implemented for admin-only testing; G3/G5/G7 runtime acceptance still pending.**  
**No production JAR and no production Simulator claim.**

## 1. Phạm vi đã thực hiện

| Feature | Đường code | Kết quả |
|---|---|---|
| Fish shown = fish caught | `FishAiDirector.reelWithCommit`, `FishKey`, `Processor.prepareSimulatorFish` | Item được clone từ khóa fish/rarity của agent đã cắn; không roll lại lúc kéo |
| Chống double reel | `FishAiDirector.Phase.COMMITTING`, consume một lần, terminal result | Sau terminal callback, phiên không được replay |
| Cancellable public API | `EMFFishCaughtEvent` qua `FishingProcessor.fireEvent` | Event bị hủy → không giao cá/trừ mồi/ghi stats |
| Data item EMF | `FishManager.getFish`, `Fish.prepareCatch`, `Fish.give` | Giữ length, lore, worth/NBT, metadata để shop và journal đọc |
| Rod/competition bonuses | `FishingProcessor.prepareSimulatorBonuses` | Double-catch, XP multiplier; animation chỉ sau item delivery |
| Bait | `FishAttractionPolicy.Snapshot` + `BaitHandler.handleFish` | Bait chỉ tiêu thụ khi giao vật phẩm thành công và modifier phù hợp |
| Journal/history | `SimulatorCatchEventGate`, `EMFFishListener.recordDeliveredSimulatorCatch` | Deferred nội bộ theo identity fish+UUID; chỉ ghi sau item delivery |
| Reward/metrics/competition | `Processor.completeSimulatorFish` | Dùng chung đường EMF; one-time guard |
| Inventory full | `SimulatorCatchService.deliverItem` | Nếu không chứa đủ một stack, thử drop an toàn tại player; không partial-insert có chủ ý |
| Native loot compatibility | `legacyLootCompatibilityRequired` | Nếu thiếu BAIT_LOOT/native treasure bridge, giữ legacy EMF thay vì bỏ loot |
| Diagnostics | `/emf admin simulator status` | Reward enabled, paper acknowledgement, compatibility, delivery/rejected/error counts |
| Safety | `simulator.reward-bridge` | Mặc định OFF; yêu cầu admin manual test G3/G5, 2 quyền admin và AI probe |

## 2. Pipeline giao thưởng

```text
Server PlayerFishEvent.REEL_IN (HIGHEST)
  → active session + hook/zone/rod/bait/permission/expiry verification
  → FishAiDirector: BITE_READY → COMMITTING (chặn recursive reel)
  → resolve FishAgent.FishKey (đúng cá đã nhìn thấy)
  → FishManager.getFish(rarityId, fishId) [clone]
  → Fish.prepareCatch() [length]
  → EMFFishCaughtEvent once [cancellable]
      ├─ cancelled/failure → release fish, no bait, no item, no stats
      └─ accepted → Fish.give() + pure double-catch/XP calculations
  → REVALIDATE session/zone/player/hook/current rod/native event
  → consume reserved FishAgent once (irreversible)
  → deliver full stack to inventory OR world drop
      ├─ failed → terminal fail-closed, never retry; no bait/stats
      └─ success → EMF internal catch history → bait → catch effects/rewards
                    → broadcast/message/metrics → competition → XP → animations
  → cleanup session, hook and packet visual
```

**Guarantee scope:** at-most-once item delivery attempt per *active in-memory session* on Paper's owner thread. Không thể bảo đảm distributed exactly-once khi server crash hoặc reward commands thuộc plugin bên ngoài; không sử dụng audit như transaction ledger.

**Native loot:** event cancellation/loot suppression vẫn cần test thật trên Paper. Runtime gate không tự xác minh G3; `paper-gates-verified` chỉ là xác nhận do admin cấu hình **sau khi thực sự test**.

## 3. Cải tiến có ý nghĩa

### 3.1. DB history không ghi trước delivery

Ban đầu `EMFFishListener` nhận `EMFFishCaughtEvent` tại `MONITOR` và enqueue snapshot ngay trong event. Điều này không phù hợp với Simulator khi item chưa được đưa cho player.

Phase 7 dùng `SimulatorCatchEventGate`: trước dispatch đặt context `playerUUID + Fish instance`; `EMFFishListener` hoãn **chỉ event cùng identity**, không hoãn event vanilla hoặc event lồng nhau khác. Sau delivery, `Processor.completeSimulatorFish` ghi `CatchStatsSnapshot` một lần qua API có sẵn. ThreadLocal luôn cleanup bằng `finally`.

### 3.2. Không mất các category loot chưa được mô phỏng

EMF `fishing.only-fish: true` giữ treasure vanilla; `bait.catch-percentage > 0` đôi khi trả bait item; mcMMO/AuraSkills có thể tác động loot khi cấu hình cho phép. Vì Simulator **chưa có packet agent/loot table adapter tương đương**, reward bridge tự chọn **LEGACY_EMF** cho cast đó trước khi takeover. Không dùng một cá COD packet để giả làm bait hoặc enchanted book.

**Chú ý:** `bait.catch-percentage` mặc định EMF là **2.5**, vì vậy muốn test reward bridge cần thiết lập `bait.catch-percentage: 0` trên **server test**, không đổi cài đặt production vô tình.

### 3.3. Giới hạn an toàn Phase 7

Ngay cả khi `simulator.reward-bridge.enabled: true`, chỉ người có cả `emf.admin.simulator.preview` và `emf.admin.simulator.probe` mới được vào admin simulator/nhận thưởng qua bridge. `simulator.enabled`, region, `native-hook-probe`, `ambient-preview`, `ai-preview` cũng phải bật và hook runtime hỗ trợ.

## 4. Test kết quả

| Test | Result |
|---|---|
| Parse 446 Java sources (Java 21 parser) | PASS, syntax only |
| Baseline smoke Phase 01 | PASS |
| Simulator smoke Phase 02–03 | PASS |
| Packet population smoke Phase 04–05 | PASS (mock transport) |
| Fish AI smoke Phase 06 | PASS |
| Phase 07 transaction smoke | PASS: event cancel, identity, duplicate reel, late invalidation, reentrant reload, delivery fail |
| Phase 07 stats-gate smoke | PASS: identity matching, nested events, exception cleanup |
| `SimulatorSettings.java` isolated JDK compile | PASS |
| `simulator.yml` + 18 locale YAML parse | PASS; new message keys checked in EN/VI/VN |
| Full Gradle build / Shadow JAR | **BLOCKED: Gradle 9.5.1 distribution cannot be downloaded (DNS)** |
| Paper / PacketEvents live test | NOT RUN |
| Load test 30–100 anglers | NOT RUN |

> Java parser, isolated compile and smoke tests are not a substitute for a complete Gradle compile or Paper server integration. Artifact is **source-only**.

### Re-run tests

```bash
bash qa/run-parse-only.sh
bash qa/run-offline-smoke.sh
bash qa/run-simulator-phase23-smoke.sh
bash qa/run-simulator-phase45-smoke.sh
bash qa/run-simulator-phase6-smoke.sh
bash qa/run-simulator-phase7-smoke.sh
bash qa/run-simulator-phase7-stats-smoke.sh
./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon
```

## 5. Chưa hoàn thành / release blockers

1. **G3:** prove suppress-native-bite / loot / EXP trên Paper 1.21.4+ với đa dạng rod, plugin cancellation và `PlayerFishEvent` ordering.
2. **G5:** prove spawn/move/despawn fish packet trên PacketEvents bản target, world change/chunk unload/session cleanup.
3. **G7:** full Gradle compile, kiểm thử inventory + item spawn cancellation, plugin hủy `EMFFishCaughtEvent`, DB/competition/rod/bait/size, đối chiếu tất cả reward source.
4. **BAIT_LOOT_AGENT:** chưa triển khai. Legacy fallback là cố ý, không claim tương thích bait-loot Simulator.
5. **VANILLA_TREASURE/JUNK loot table bridge:** chưa triển khai. `fishing.only-fish: true` fallback legacy.
6. External economy command có side effects và server crash cần ledger nếu muốn strong durable exactly-once.
7. Rủi ro inventory exception sau item đã vào inventory: phát thưởng có thể thành công một phần nhưng post stats không chạy. Cần test lỗi giả lập / compensation policy trước production.
8. Folia: **NOT SUPPORTED/VERIFIED.** Không thêm `folia-supported: true` dựa trên thiết kế này.

## 6. Phase tiếp theo

- **Phase 7.1 hardening / Gate verification** (ưu tiên): full compile và Paper integration matrix; sửa mọi vấn đề native hook/item transaction phát hiện.
- **Phase 8:** visuals, HUD, cinema, cấu hình và diagnostics; renderer custom ITEM_DISPLAY cần kiểm thử riêng.
- **Phase 9:** performance profiling và gradual rollout trước khi mở cho player bình thường.

