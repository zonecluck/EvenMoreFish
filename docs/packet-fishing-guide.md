# Hướng dẫn: Hồ câu Packet, FishPacket và Cinematic

Cập nhật: 2026-10-12. Tài liệu này giải thích cách dùng và mở rộng hệ thống packet fish, hồ câu và cinematic của EvenMoreFish.

---

## 1. Tổng quan kiến trúc

| Thành phần | File chính | Vai trò |
|---|---|---|
| **Hologram mồi** | `FishingHookHolograms.java` | TextDisplay trên phao: trạng thái chờ/cắn/hụt + countdown native |
| **Cinematic bắt cá** | `VanillaCatchCinematicService.java` | ItemDisplay riêng cho người câu, bay từ mặt nước về người chơi (Rare+) |
| **FishPacket câu cá** | `HookedFishPacketService.java` | Cá packet (COD) quay quanh phao khi BITE trong hồ có `hookedVisual` |
| **Cinematic packet** | `PacketCatchCinematicService.java` | Cá packet với timeline Epic/Legendary (xoáy, reveal, delivery) |
| **Transport** | `PacketTransport.java` + `Paper121PacketEventsTransport.java` | Gửi packet spawn/teleport/destroy qua PacketEvents (chỉ 1.21) |
| **Hồ câu** | `FishingPoolService.java` | Vùng nước có bonus tốc độ/rarity, lưu persistent trong `fishing-pools.yml` |

---

## 2. Cài đặt & bật Packet Fish

### Yêu cầu

- **Paper 1.21+** (hoặc fork). Các biến thể khác dùng `PacketTransport.unavailable()` — packet fish sẽ không render.
- **PacketEvents plugin** đã cài trên server (compileOnly trong EMF, không shaded). Phiên bản đã test: 2.13+.
- Không cần bật `simulator.enabled` hay `native-hook-probe` — FishPacket câu cá hoạt động độc lập.

### Kiểm tra

```bash
/plugins                       # PacketEvents phải xanh
/emf admin pool list           # Xem hồ đã tạo
```

Nếu transport không khả dụng, plugin log `[Simulator] PacketEvents transport disabled` và bỏ qua phần cá packet mà không ảnh hưởng câu cá.

---

## 3. Quản lý hồ câu (Pool)

### Lệnh admin

Quyền: `emf.admin` + `emf.admin.pool`

```text
/emf admin pool create <id> <radius> [speed-percent] [rare-multiplier]
/emf admin pool remove <id>
/emf admin pool list
/emf admin pool info <id>
/emf admin reload
```

| Tham số | Giới hạn | Mặc định |
|---|---|---|
| `id` | 1–48 ký tự ASCII, `_`, `-` | bắt buộc |
| `radius` | 1–64 block | bắt buộc |
| `speed-percent` | 0–85 | `default-speed-percent` (25) |
| `rare-multiplier` | 1–10 | `default-rare-multiplier` (1.5) |

### Ví dụ

```text
/emf admin pool create ho-vip 15 40 3
/emf admin pool create ho-thuong 8
```

- `ho-vip`: bán kính 15, giảm chờ 40%, trọng số rarity ×3.
- `ho-thuong`: bán kính 8, dùng mặc định config.

Nhắm vào mặt nước trong 32 block khi chạy `create`. Nếu không nhắm được, tâm hồ là vị trí người tạo. `create`/`remove` lưu ngay bằng thay thế file nguyên tử — restart không mất hồ.

### Config `fishing-pools.yml`

```yaml
fishing-pools:
  enabled: true               # Công tắc tổng
  vertical-tolerance: 2.0     # Sai lệch độ cao tối đa để phao được tính trong hồ
  default-speed-percent: 25.0
  default-rare-multiplier: 1.5
  rare-rarity-ids: [Uncommon, Rare, Epic, Legendary, Mythical]
  particles:
    enabled: true             # Vòng hạt END_ROD quanh mép hồ
    ring-points: 32
    viewer-distance: 48
  pools: {}                   # Lưu tự động, không sửa tay khi server chạy
```

- `Common` **luôn bị loại** khỏi nhân trọng số, kể cả khi thêm vào `rare-rarity-ids`.
- `rare-multiplier` nhân **trọng số**, không phải xác suất bắt được. ×2 Rare với Common=10/Rare=10 → Rare từ 50% → ~66.7%.

---

## 4. Hồ câu động từ plugin ngoài (FishingLakeProvider)

Khi hồ tĩnh không đủ, plugin khác có thể đăng ký `FishingLakeProvider` để cấp bonus động theo đảo/khu vực.

### API

```java
package com.oheers.fish.api.fishing;

public interface FishingLakeProvider {
    record Bonus(String id, double speedPercent, double rareMultiplier, boolean hookedVisual) {}
    Bonus resolve(Player player, Location hookLocation); // null = không phải hồ của bạn
}
```

### Đăng ký

```java
// Trong onEnable() của plugin bạn
Bukkit.getServicesManager().register(
    FishingLakeProvider.class,
    new MyLakeProvider(),   // implements FishingLakeProvider
    myPlugin,
    ServicePriority.High    // Ưu tiên cao hơn hồ tĩnh
);
```

### Ví dụ: hồ T10 của đảo

```java
public final class IslandLakeProvider implements FishingLakeProvider {
    @Override
    public Bonus resolve(Player player, Location hook) {
        Island island = getIslandAt(hook);
        if (island == null || island.getTier() < 10) return null;
        return new Bonus("island:" + island.getId(), 25, 2.0, true);
    }
}
```

- `hookedVisual: true` → khi BITE, `HookedFishPacketService` spawn cá COD packet quay quanh phao trong 3 giây (chỉ người câu thấy).
- `hookedVisual: false` → chỉ áp bonus tốc độ/rarity, không spawn cá packet.

EMF tra `ServicesManager` trực tiếp mỗi lần resolve — hỗ trợ đăng ký/gỡ sau enable/disable/reload.

---

## 5. FishPacket lifecycle (cá packet quanh mồi)

`HookedFishPacketService` chạy hoàn toàn trên main thread, không dùng Bukkit entity:

```
BITE event (MONITOR, không cancelled)
  → check: bonus.hookedVisual + preferences + transport.available
  → FishAgent(id ngẫu nhiên, entityId = PacketEntityIdAllocator.next(), zoneId = bonus.id())
  → transport.spawn(viewer, fish)           // gửi SpawnEntity + EntityMetadata
  → scene lưu vào Map<viewerUUID, Scene>
  → ticker mỗi 2 tick:
      fish.waypoint(quanh phao, bob ±0.06)
      fish.move(ticks, 0.16)               // stepTowards, yaw ease
      transport.teleport(viewer, fish)      // gửi EntityTeleport
  → dọn khi: 60 tick hết / hook invalid / player quit / đổi world / reload
  → transport.destroy(viewer, entityId)     // gửi DestroyEntities
```

### Giới hạn an toàn

- `MAX_SCENES = 16` — tối đa 16 cảnh đồng thời.
- `MAX_CLEANUP = 64` — hàng đợi retry destroy nếu transport fail.
- `DURATION_TICKS = 60` — cảnh tự kết thúc sau 3 giây.
- `shownHooks` — một hook chỉ spawn một lần (không lặp lại khi BITE bị hủy/rớt).
- Distance > 32 block từ người câu → hủy cảnh.

### Khi transport lỗi

- `spawn()` fail → ghi vào `PacketCleanupQueue`, retry destroy ở tick sau. Không spawn mới cho viewer đó khi còn pending.
- `teleport()` fail → dọn cảnh ngay.
- `destroy()` fail → ID vẫn nằm trong queue, retry có giới hạn.

---

## 6. Cinematic bắt cá Rare+

`VanillaCatchCinematicService` dùng **ItemDisplay** (Bukkit entity ẩn với người khác), không cần PacketEvents.

### Kích hoạt

```yaml
# config.yml
animations:
  rare-catch:
    enabled: true
    cinematic:
      enabled: true
      rarities: [Rare, Epic, Legendary, Mythical]
      excluded-rarities: [Common]
      cooldown-ticks: 60        # 3 giây giữa 2 cảnh của cùng người
      max-concurrent: 8         # Tối đa 8 cảnh đồng thời
```

### Timeline

| Rarity | Thời lượng | Hiệu ứng |
|---|---|---|
| Rare / eligible khác | 26 tick (~1.3s) | Cá nổi lên, xoay nhẹ, bay về người câu |
| Epic | 62 tick (~3.1s) | Xoáy tím, surface break, hero reveal, delivery |
| Legendary / Mythical | 80 tick (~4s) | Halo vàng, xoáy rộng, reveal dài |

Cảnh hủy khi: người câu di chuyển > 3 block, đổi world, quit, recast, reload, hoặc hết duration.

### HUD preferences (1.21)

```text
/emf hud off            # Tắt mọi hiệu ứng
/emf hud minimal        # Chỉ HUD đếm, không particle/cinematic
/emf hud balanced       # Cinematic ngắn (arc thấp)
/emf hud cinematic      # Cinematic đầy đủ Epic/Legendary
/emf hud reduced-motion # Không cinematic, giảm chuyển động
```

Quyền: `emf.simulator.preferences` (chỉ trên adapter 1.21).

---

## 7. Tạo hồ Packet tùy biến (custom zone)

Nếu muốn hồ có hiệu ứng riêng không qua `fishing-pools.yml`:

1. **Tạo class** implements `FishingLakeProvider`, trả `Bonus` với `hookedVisual=true`.
2. **Đăng ký** `ServicesManager` với priority cao hơn hồ tĩnh.
3. **(Tùy chọn) Custom fish render**: `FishRenderSpec.vanilla(COD)` là mặc định. Muốn ItemDisplay với custom model, override `FishRenderPolicy` và gọi `PacketCatchCinematicService.modelPolicy()` — nhưng đó là phần cinematic packet, không phải hooked visual.

**Lưu ý**: FishPacket câu cá (`HookedFishPacketService`) luôn spawn `FishVisualModel.COD` — không đổi được qua config. Muốn cá khác phải sửa source hoặc dùng cinematic packet (`simulator/cinematics.yml` với `fish-overrides`).

---

## 8. Troubleshooting

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Không thấy cá packet | PacketEvents chưa cài / không phải 1.21 | Cài PacketEvents ≥2.13, check `/plugins` |
| `[Simulator] transport disabled` | PacketEvents lỗi hoặc player offline | Xem log, restart server |
| Hồ không áp dụng | `fishing-pools.enabled: false` | Bật trong `fishing-pools.yml`, `/emf admin reload` |
| Không thấy cinematic | `cinematic.enabled: false` hoặc HUD off | Check config + `/emf hud cinematic` |
| Không nhận cá và không có cinematic, console báo `NoSuchFieldError: HIDE_ITEM_SPECIFICS` | JAR cũ liên kết cờ tooltip Paper 1.20 đã đổi tên trên Paper 1.21 | Thay JAR đã sửa tương thích `ItemFlag` và restart; không cần đổi YAML hay bật Simulator |
| Cần Minecraft thường không nhận cá EMF, cần custom được phép câu | `fishing.require-custom-rod: true` | Nếu muốn cho phép cả hai loại cần, đặt riêng khóa này thành `false`, rồi reload cấu hình hoặc restart |
| Cá packet biến mất ngay | Player đổi world / quit / recast | Đây là hành vi đúng — cleanup tự động |
| Hologram không hiện | Paper <1.19.4 không có TextDisplay | Chỉ hỗ trợ Paper 1.20+ |

Hologram trên phao dùng nền trong suốt cho cả trạng thái chờ, cắn câu và hụt cá; vẫn giữ bóng chữ để dễ đọc. Thay đổi này nằm trong JAR, không cần cấu hình bổ sung.

---

## 9. Tắt / rollback

- Tắt hồ: `fishing-pools.enabled: false` → `/emf admin reload` (định nghĩa hồ vẫn giữ).
- Tắt vòng hạt hồ: `fishing-pools.particles.enabled: false`.
- Tắt cinematic: `animations.rare-catch.cinematic.enabled: false`.
- Tắt toàn bộ hiệu ứng cá hiếm: `animations.rare-catch.enabled: false`.
- Thay JAR mới: **phải restart server**, reload không đủ.
