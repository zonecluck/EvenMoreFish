# EvenMoreFish Simulator V2 — Phase 04–05 Implementation Report

> Ngày: 2026-10-11  
> Baseline: `EvenMoreFish-Simulator-Phase03` (bao gồm sửa đổi Phase 01–03)  
> Trạng thái: **Đã triển khai source preview; chưa full Gradle compile/Paper runtime; chưa production-ready**

## 1. Phạm vi được phép

- **Phase 04:** Chỉ mục mặt nước trong các chunk đã tải, quần thể cá ảo phía server, cá dùng chung/cá riêng, capacity & TTL, movement và bảo vệ một cá/một reservation.
- **Phase 05:** Packet-only vanilla fish (cod/salmon/tropical fish/pufferfish), quản lý virtual entity ID, hiển thị theo viewer, LOD, giới hạn packet, cleanup. Adapter PacketEvents **chỉ** trong biến thể Paper 1.21.
- **Chế độ kích hoạt:** Chỉ **admin ambient preview** với quyền `emf.admin.simulator.preview` và cấu hình mặc định **tắt**. Đàn cá chỉ hiện cho admin đủ quyền đứng trong vùng Simulator hợp lệ. Không có mob cá thật.
- **Không bao gồm:** Thay engine PlayerFishEvent, cá cắn phao, minigame kéo cá, item/EXP reward, hooking shop/competition mới, custom ItemDisplay/resource pack thực thi, release JAR.
- **Gate G3 chưa chứng minh:** Native loot suppression mới chỉ có probe thử nghiệm từ Phase 03, vì không có Paper server runtime. Không bật Simulator cho player production.

## 2. Chi tiết code

### 2.1 Core, không phụ thuộc thư viện packet

| Module | File | Mục đích |
|---|---|---|
| Domain | `population/FishKey.java`, `Vec3.java`, `WaterPoint.java`, `FishVisualModel.java` | Định danh EMF + vị trí 3D, không giữ Bukkit `Player`/`Entity` |
| Agent | `population/FishAgent.java` | State `WANDERING/RESERVED/CAUGHT/DESPAWNED`, waypoint, last movement/yaw, owner riêng |
| Population | `population/FishPopulationService.java` | Global cap, zone cap, private cap, per-viewer cap, reservation/consume/release, expiry, cleanup |
| Water index | `water/WaterIndexService.java` | Scan từng cột trong chunk đã load, heightmap + độ sâu, cache LRU/cap, invalidation, hàng đợi quét |
| Catalog | `catalog/FishCatalogAdapter.java` | Đọc định nghĩa `FishManager`/rarity/fish (CATCH hoặc BOTH), weighted sampling không tạo reward |
| Transport API | `visual/PacketTransport.java` | Giao diện spawn/teleport/destroy, fallback unavailable, không import PacketEvents vào core |
| Entity ID | `visual/PacketEntityIdAllocator.java` | Cấp ID ảo không tái sử dụng trong cùng tiến trình |
| Viewer | `visual/PacketVisibilityService.java` | Hide private, filter world/zone/distance, nearest-agent cap, LOD, budget spawn/metadata/move/destroy, retry cleanup |
| Mapping | `visual/VisualMappingConfig.java` | `simulator/visuals.yml`, mapping rarity/fish -> vanilla fish model, malformed YAML fallback an toàn |
| Runtime | `AmbientPreviewService.java` | Tick chung, admin permission, spawn/move/scan/cleanup, water-flow debounce, không đụng reward |

### 2.2 PacketEvents adapter

- `even-more-fish-plugin-1-21/src/main/java/com/oheers/fish/Paper121PacketEventsTransport.java`
- Dùng `WrapperPlayServerSpawnEntity`, `WrapperPlayServerEntityMetadata`, `WrapperPlayServerEntityTeleport`, `WrapperPlayServerDestroyEntities`.
- Một lần spawn tiêu thụ **2 packet** (spawn + metadata/no-gravity); teleport/destroy 1 packet.
- Kiểm tra player online/world, PacketEvents plugin và API, xử lý `LinkageError`/`RuntimeException` fail-closed; lỗi adapter sẽ ngừng preview, không ngừng EvenMoreFish truyền thống.
- `even-more-fish-plugin-1-21/build.gradle.kts`: `compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")`, khai báo `softDepend` khi tạo plugin.yml. Thư viện **không shade** vào plugin.
- Các biến thể Paper 1.20/26.1 khác tiếp tục dùng `PacketTransport.unavailable()`; không tuyên bố hỗ trợ packet preview trên biến thể này.

### 2.3 Bootstrap, reload và lệnh

- `EvenMoreFish` tạo adapter thông qua protected factory method; `onCatalogReady` được gọi sau khi load/reload `FishManager`.
- `SimulatorModule` sở hữu preview và chung vòng ticker, cleanup khi emergency-off/reload/disable.
- `SimulatorSubcommand status` in thêm thông tin `active`, `agents`, `private`, `viewers`, `visible`, `water`, `queue`, `sent`, `deferred`. Message qua locale EN/VI/VN.
- Config mới: `simulator.yml > simulator.ambient-preview` (21 tham số, tất cả được validate) và `simulator/visuals.yml` (mapping cá hiển thị).
- **Giữ nguyên** file `config.yml`/`version: 8` của EMF và migration legacy; Simulator dùng `config-version: 1` ở file riêng.

## 3. Cơ chế quản lý tài nguyên

- `ambient-preview.enabled: false` và `simulator.enabled: false` theo mặc định.
- Preview chạy Paper main thread; **không Folia-ready**; không tạo scheduler per-player.
- Không load/generate chunk, kiểm tra `isChunkLoaded` trước truy cập block.
- Scan mặc định tối đa 256 cột + ngân sách thời gian ~0.6 ms/tick (soft bound giữa cột, không có hard guarantee cho mỗi API call).
- Agent mặc định toàn server ≤160; zone ≤48; cá riêng ≤24; mỗi owner ≤2; viewer ≤8; hiển thị ≤20 con/viewer; packet tổng ≤800/tick.
- Các giá trị là **giới hạn thiết kế**, không phải kết quả đo TPS/MSPT thực tế.
- Nhận diện `BlockBreak/Place`, bucket, water flow, chunk unload; flow invalidation debounce tối thiểu 40 tick cùng chunk và cache debounce tối đa 512 entries.
- Private fish bị lọc trước khi gửi packet; public fish sử dụng một identity server, được render cùng entity ID cho viewer được phép.
- Despawn/TTL/quit/world change/reload/disable dọn population và những viewer hiện hữu; packet destroy lỗi được retry trong tick kế tiếp khi hoạt động bình thường. Khi plugin/PacketEvents ngừng, destroy chỉ best-effort.

## 4. Dùng thử trên server test (không production)

1. Chuẩn bị bản JAR sau khi Gradle build thành công; cài Paper 1.21.4+ với **PacketEvents** bản API tương thích (thiết kế adapter cho 2.14.0).
2. Backup cấu hình EMF, cài và start server; xác nhận legacy EMF vẫn câu cá bình thường vì Simulator mặc định tắt.
3. Chỉnh `plugins/EvenMoreFish/simulator.yml`:
   ```yaml
   simulator:
     enabled: true
     dependency:
       on-unavailable: LEGACY_EMF
     ambient-preview:
       enabled: true
   zones:
     central_lake:
       enabled: true
       type: CUBOID
       world: world
       min: {x: -120, y: 55, z: 220}
       max: {x: 120, y: 115, z: 460}
   ```
   Các zone còn lại có thể giữ `enabled: false`; tọa độ và world phải thay theo server test.
4. OP hoặc grant `emf.admin.simulator.preview`. Chạy `/emf admin simulator reload` rồi `/emf admin simulator status`, đứng cạnh vùng hồ và kiểm tra cá ảo xuất hiện (sau khi chỉ mục nước quét xong).
5. Dùng 2 tài khoản (một admin preview, một player không có permission) để xác minh không lộ private/preview sang player thường. Chạy `spark profiler`/network inspector trong tình huống nhiều admin thử nghiệm.
6. **Không bật** `native-hook-probe.enabled` trên production. Probe Phase 03 độc lập với preview cá, không trao thưởng.

## 5. Bài kiểm tra đã chạy

| Kiểm thử | Trạng thái | Giới hạn |
|---|---|---|
| Java 21 parse 442 file `.java` | **PASS** | Kiểm tra cú pháp, không resolve external symbols/type-check |
| Phase 01 smoke (dirty flush, retry, fractional boost, RNG, DB worker) | **PASS** | Headless JVM |
| Phase 02–03 smoke (10 zone tests, probe state, offhand) | **PASS** | Headless JVM, Bukkit stubs |
| Phase 04–05 smoke (domain chính + visibility) | **PASS** | Compile và chạy production pure-Java classes |
| 100-viewer/1000-agent stress simulation, packet-count bounds | **PASS** | Mô phỏng mock transport, không đo MSPT/network |
| YAML structure kiểm tra | **PASS** | Static resources |
| Gradle `test` + `shadowJar` | **BLOCKED** | `UnknownHostException: services.gradle.org` khi tải Gradle 9.5.1 |
| Paper runtime, PacketEvents render, native hook G3 | **NOT TESTED** | Không có Paper server/dependency runtime |
| Folia | **NOT SUPPORTED / NOT TESTED** | Cross-region operations chưa có ownership model |

### Câu lệnh kiểm tra lại

```bash
./qa/run-parse-only.sh
./qa/run-offline-smoke.sh
./qa/run-simulator-phase23-smoke.sh
./qa/run-simulator-phase45-smoke.sh
./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar --no-daemon
```

## 6. Những phần không thể claim hoàn thành

1. **G3**: cần test Paper để xác nhận điều khiển native bite/loot trong PlayerFishEvent mà không rơi item/EXP trùng.
2. **G5**: packet wrappers/metadata không được kiểm thử live. Thông số PacketEvents tham chiếu Javadoc 2.14.0, nhưng compile runtime vẫn cần thực hiện.
3. Model resource pack `ITEM_DISPLAY` chưa thực thi; file mapping chứa `renderer: ITEM_DISPLAY` thì warn và dùng vanilla fallback.
4. Weighted sampling của preview **không** kiểm điều kiện player/location, bait, rod hay competition; đây chỉ là preview. Phase 06 sẽ cần resolver chuẩn EMF theo người chơi mới được ràng buộc fish identity vào gameplay.
5. PacketErrors, chunk unload, water mutation, world switch, respawn, large-player viewer removal chưa được test trên client Minecraft thật.
6. Phase 06–07 chưa có AI seek bait, bite/reel, catch/reward transaction; câu cá legacy vẫn giữ nguyên.

## 7. Điều kiện nghiệm thu Phase 04–05

Xem `qa/PHASE_05_PAPER_TEST_MATRIX.md`. Tiêu chí bắt buộc trước khi chuyển sản phẩm tới player:

- Full Gradle build PASS.
- Preview chỉ admin: player không được quyền không nhìn thấy packet fish.
- 2 admin thấy cùng shared fish; private fish chỉ hiện cho owner.
- Khi unload chunk/quit/world/reload, entity ảo biến mất; không rò rỉ ID/session/packet.
- Rarity model fallback hiển thị đúng; PacketEvents thiếu không làm ngừng EMF.
- Player vẫn câu cá vanilla/EMF, reward/competition/shop unchanged.
- Profiler chứng minh ngân sách packet và MSPT trong môi trường mục tiêu.

## 8. Phase tiếp theo

- **G3/G5 integration tests** là cổng bắt buộc trước reward rollout.
- Phase 06: Fish AI/attraction/reservation -> bite/reel với thời gian phản ứng, kiểm tra bait/rod/region/permission và identity của cá nhìn thấy.
- Phase 07: `CatchCommit` idempotent, chia loot vanilla/bait/EMF, compatibility event/competition/shop và audit giao dịch; không double drop.
- Phase 08–09: animation/cinematic/HUD, custom ItemDisplay packet mapping có versioned metadata, profiling/QA/release.

## 9. Tài liệu nguồn kỹ thuật

- PacketEvents prebuilt dependency: https://github.com/retrooper/packetevents/wiki/Depending-on-pre%E2%80%90built-PacketEvents
- API wrapper Javadoc: https://javadocs.packetevents.com/
- Paper 1.21.4 API: https://jd.papermc.io/paper/1.21.4/

> Kết luận: code được triển khai **ở mức source preview có test phần lõi**, không có bằng chứng để tuyên bố bản này build được thành JAR production hay đã hiển thị thành công trên server thật.
