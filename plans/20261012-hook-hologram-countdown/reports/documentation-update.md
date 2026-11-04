---
date: 2026-10-12
status: done
scope: operator-documentation
---

# Tài liệu hologram phao và đếm ngược

## Nội dung đã cập nhật

Đã bổ sung mục hologram phao trong [hướng dẫn hồ câu và cinematic](../../../docs/fishing-pools-and-cinematics.md), giữ nguyên toàn bộ hướng dẫn Epic/Legendary có trước. Ghi rõ trạng thái chờ aqua, BITE gradient trên hologram, 20 vạch cyan/xám tối cùng số giây trắng và chu kỳ refresh chữ 2 tick.

Tài liệu phân biệt bộ đếm wait/approach/nibble đọc trực tiếp với ETA thời gian thực; mapping thiếu không sinh số đoán. BITE được lấy sau callback, cửa sổ bằng 0 trả về chờ để xử lý hủy muộn. Legacy 1.20.1 chỉ có getter wait được bảo đảm; tên field native hỗ trợ bản hiện đại 1.21.11.

Đã ghi rõ TextDisplay vẫn hiển thị trong world, nhánh hook UI không gọi `showTitle`/`clearTitle`, giữ title khác và không đổi đồng hồ câu/Lure/pool/reward. Lifecycle dọn display và dừng updater được mô tả theo mã nguồn; không tuyên bố config reload sẽ teardown hologram. Thay JAR cần restart, không đổi cấu hình runtime.

## Đối chiếu và xác minh

- Đọc tài liệu hiện có, [kế hoạch](../plan.md), `NativeHookTimers`, `FishingHookHolograms`, `HookCountdownLine` và các điểm gọi trong `FishingProcessor`.
- Đối chiếu nội dung chữ/màu, 20 vạch, tick refresh, BITE sau event, bộ đếm unknown/zero, lifecycle và phạm vi title với mã nguồn. Giới hạn mapping phiên bản dựa trên thông tin điều phối viên cung cấp cho cập nhật này.
- Kiểm tra thủ công liên kết tương đối và whitespace của hai tệp trong phạm vi. Script `.claude/scripts/validate-docs.cjs` không có trong checkout.

## Giới hạn bằng chứng và bước tiếp theo

Test/build cuối đang chạy; chưa xác nhận upload hoặc restart. Không ghi số test cuối, không chứng nhận render/cleanup client hay MSPT thực tế. Admin kiểm tra mục hologram sau restart, điều phối viên ghi kết quả build và triển khai trong báo cáo xác minh riêng.
