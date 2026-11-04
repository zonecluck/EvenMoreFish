# 🐠 Phase 15 — Custom Packet Fish Models (Staging Guide)

## Mục tiêu

Dùng `ItemDisplay` ảo để hiển thị model cá theo `Rarity:FishKey`, nhưng giữ nguyên cá/rarity/reward EMF. Player chưa có pack hoặc client không hỗ trợ vẫn nhìn thấy cá vanilla tương ứng.

## Yêu cầu

- Paper 1.21.4+ (target), Java 21, PacketEvents 2.14.0.
- Một nguồn **gửi resource pack** cho client, ví dụ pack manager của server; plugin này **không tự host hoặc tự gửi** resource pack.
- Pack phải chứa `assets/ledat/items/fish/golden_koi.json` và model tương ứng, hoặc tên item-model do bạn tùy chỉnh.
- Pack **phải được gửi với UUID giống `resource-pack.id`** trong `simulator/models.yml`. ACK `SUCCESSFULLY_LOADED` của chính UUID đó là điều kiện mở custom model.
- G3/G5/G7 chưa nghiệm thu: staging, admin preview only.

## Cấu hình mẫu

```yaml
config-version: 1
enabled: false # chỉ bật sau khi G5 kiểm chứng thực tế
resource-pack:
  id: 'REPLACE_WITH_ACTUAL_RESOURCE_PACK_UUID'
fish-overrides:
  'Legendary:Golden Koi':
    material: PAPER
    item-model: 'ledat:fish/golden_koi'
    scale: 0.75
    interpolation-ticks: 2
rarity-overrides:
  Epic:
    material: PAPER
    item-model: 'ledat:fish/epic'
    scale: 0.7
    interpolation-ticks: 2
```

⚠️ Trên file thật, `id` phải là UUID hợp lệ (định dạng `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`); dòng `REPLACE...` chỉ là ví dụ, nếu để nguyên loader sẽ từ chối reload.

**Định danh cá:** phần `'Legendary:Golden Koi'` phải khớp tên rarity + tên cá có trong catalog EMF. `item-model` *không* phải đường dẫn file hệ điều hành, nó là tên tài nguyên item model Minecraft.

**Example resource pack:** thư mục `docs/simulator/resource-pack-example/`. Đây là **mẫu cấu trúc** dùng texture cá cod vanilla; muốn model 3D đẹp cần thay bằng file JSON/texture của bạn. `pack.mcmeta` mẫu dành cho Minecraft **1.21.4** (format 46), cần điều chỉnh cho client đích 1.21.11 hoặc bản khác.

## Nguyên tắc vận hành

1. Trên staging, giữ `simulator.yml` và `ambient-preview` tắt khi mới cài source; cần vượt qua build và G5 trước.
2. Cấu hình pack manager phát **đúng UUID** và tài nguyên model tương ứng cho player. Một ID ở YAML **không tự làm client nhận resource pack**.
3. Khi player báo `SUCCESSFULLY_LOADED` với đúng pack UUID, model custom có thể được gửi cho player đó.
4. Khi player từ chối/pack lỗi/đổi UUID, visual được **destroy rồi spawn lại** ở dạng vanilla vào lần sync tiếp theo.
5. Khi đổi nội dung pack, cập nhật UUID cùng pack manager để cache ACK cũ không gây sử dụng model sai phiên bản.
6. Kiểm tra `/emf admin simulator status` để xem phần `model-status`, số viewer ready, số swap và ACK thất bại.
7. Nếu metadata hoặc model vô hình: tắt `enabled: false`, reload, kiểm tra log PacketEvents, pack format/model JSON, thực hiện rollback preview.

## Các tình huống bắt buộc test thủ công

- Player A đã nhận pack: custom fish render; B từ chối: cùng cá vẫn là vanilla.
- B tải pack muộn: chỉ entity của B đổi sang custom, không thay `FishKey` hoặc gây double fish.
- A mất pack hoặc server push pack phiên bản mới: destroy old, spawn fallback/new đúng thứ tự.
- Câu thành công và mở cinematic: visual đúng, reward chỉ cấp một lần.
- Disconnect/chuyển world/reload/disable giữa cinematic: không còn ghost entity.
- Pack không có model key: client có thể hiển thị missing-model dù ACK thành công; **không thể phát hiện thiếu file trong pack chỉ qua event**.
- Server đang chạy client 1.21.1: không dùng item-model vì cần client 1.21.4+; fallback vanilla.

## Những thứ chưa có

- Trình thiết kế model 3D hoặc generator resource pack sản phẩm hoàn chỉnh.
- Resource-pack hosting/publishing, download theo player hoặc hệ thống xác nhận asset bên trong pack.
- Full Paper/PacketEvents runtime validation và G3/G5/G7.
