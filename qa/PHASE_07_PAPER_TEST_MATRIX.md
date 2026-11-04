# 🎣 Phase 07 — Paper Integration & Safety Gate Checklist

**Server thử nghiệm riêng:** Paper 1.21.4+ / Java 21, EvenMoreFish Phase 07 build, PacketEvents và các hook thực tế cần thử. **Không áp dụng ngay lên production.**

## A. Trước khi thử reward

- [ ] Bật server với tất cả `simulator.*.enabled: false`; legacy EMF câu cá, shop, competition hoạt động.
- [ ] Chạy `./gradlew :even-more-fish-plugin:test :even-more-fish-plugin-1-21:shadowJar` **thành công** ở máy có Gradle và dependency.
- [ ] Test G3: bật `simulator.enabled`, `native-hook-probe.enabled`, zone; admin probe không có reward, không xảy ra native loot/EXP, `BITE` không thoát suppression.
- [ ] Test G5: bật ambient preview, kiểm tra có packet fish bơi, không ghost entity khi player quit/teleport/world change/chunk unload/reload.
- [ ] Test G3/G5 phải PASS trước khi đặt `paper-gates-verified: true`. Không coi config flag là bằng chứng test.

## B. Bật admin reward test trong **test server** sau khi A đạt

Cài đặt `simulator.yml`:

```yaml
simulator:
  enabled: true
  native-hook-probe:
    enabled: true
  ambient-preview:
    enabled: true
  ai-preview:
    enabled: true
  reward-bridge:
    enabled: true
    paper-gates-verified: true    # Chỉ sau khi test thực tế G3/G5 PASS
    experience-min: 1
    experience-max: 6
zones:
  central_lake:
    enabled: true
    type: CUBOID
    world: world
    min: {x: -120, y: 55, z: 220}
    max: {x: 120, y: 115, z: 460}
```

Cài đặt EMF `config.yml` chỉ trên test server:

```yaml
fishing:
  only-fish: false
bait:
  catch-percentage: 0.0 # temporarily disables unsupported packet bait-loot category
# Keep native mcMMO/AuraSkills loot suppression enabled if those providers are present.
disable-mcmmo-loot: true
disable-auraskills-loot: true
```

Giữ quyền `emf.admin.simulator.preview` và `emf.admin.simulator.probe` cho admin test; **không cấp cho player**. Reload có validation. Kiểm tra `/emf admin simulator status` in `Reward bridge: enabled=true, paper-ack=true, legacy loot required=false`.

## C. Gameplay / EMF interop

| ID | Kiểm thử | Expected |
|---|---|---|
| P7-01 | Admin câu cá packet, kéo đúng bite | 1 EMF item mang fish ID/rarity của agent; 1 EMF event; 1 history và 1 leaderboard update |
| P7-02 | Kéo trước bite | Không có item, bait charge, EXP hay DB write |
| P7-03 | Bỏ lỡ bite | Cá bỏ chạy, không trao item |
| P7-04 | Double click / spam right click / tick overlap | At most 1 event/item batch cho một hook UUID |
| P7-05 | Bật addon hủy `EMFFishCaughtEvent` | Không item/bait/EXP/DB/competition; agent release/flee |
| P7-06 | Gọi reload từ event listener (reentrant) | Không post-commit/reward sau session invalidation |
| P7-07 | Offhand rod, sau đó swap main/offhand | Ràng buộc cast hand/fingerprint chính xác; không nhận buff sai |
| P7-08 | Rod có bait tăng rarity | Bait ảnh hưởng targeting, chỉ trừ sau item thành công, không trừ 2 lần |
| P7-09 | Custom rod disallow-baits | Bait không ảnh hưởng, không tiêu hao |
| P7-10 | Competition active / competition-only | Score +1 cho catch hợp lệ, không score khi fail/cancel |
| P7-11 | Double catch 100% bằng rod/buff | Hai item trong batch, một catch event/competition record (như EMF cũ) |
| P7-12 | EXP multiplier | Chỉ tăng XP một lần theo cấu hình và buff; native exp=0 |
| P7-13 | Inventory đầy + world drop được | Một item entity spawn gần player, item giữ đúng EMF NBT |
| P7-14 | Inventory đầy + ItemSpawnEvent bị hủy | Fail closed, no retry; kiểm tra status failed và console |
| P7-15 | Region denial / WorldGuard / RedProtect | Không takeover vùng cấm; không bypass permission |
| P7-16 | `fishing.only-fish: true` | Admin cast fallback LEGACY EMF để giữ native treasure |
| P7-17 | `bait.catch-percentage: 2.5` | Admin cast fallback LEGACY EMF để giữ bait loot |
| P7-18 | Native mcMMO/AuraSkills loot bật | Cast fallback LEGACY EMF khi provider hiện diện |
| P7-19 | Người chơi bình thường (không quyền admin probe) | Chỉ legacy EMF; không thể nhận Simulator reward |
| P7-20 | Server disable/restart/quit khi Bite/Committing | Không ghost fish/session, không award sau session bị hủy |
| P7-21 | Rarity disabled, requirements/rod changed sau targeting | Reject no bait/item/history; trace/diagnostics rõ |
| P7-22 | DB queue saturated/failure | Không block server tick; log rõ thất bại, không retry item |
| P7-23 | Test với `give-straight-to-inventory: true/false` | Full-stack insertion hoặc item drop an toàn; không dupe |
| P7-24 | Reload config invalid | Snapshot last-good giữ nguyên; không mở reward trái phép |

## D. Release gate

- [ ] **G3 PASS** thật trên Paper server, không chỉ test stub.
- [ ] **G5 PASS** thật với PacketEvents và các viewer.
- [ ] **G7 PASS**: 0 duplicate item/event/competition update, 0 wrong FishKey, 0 DB record cho canceled/failed catch.
- [ ] Test ít nhất 1, 10, 30 concurrent anglers; thu `spark` profiler / packet count / MSPT.
- [ ] Không release nếu bait loot/native treasure compatibility vẫn cần full Simulator trong zone đó: chọn LEGACY fallback hoặc xây adapter riêng.
- [ ] Không claim Folia cho build này.

