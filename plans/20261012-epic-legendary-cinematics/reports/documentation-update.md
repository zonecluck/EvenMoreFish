---
date: 2026-10-12
status: done
scope: operator-documentation
---

# Cập nhật tài liệu cinematic Epic/Legendary

## Nội dung đã cập nhật

Đã đối chiếu và sửa [hướng dẫn hồ câu và cinematic](../../../docs/fishing-pools-and-cinematics.md): native Epic 62 tick (3.1 giây ở 20 TPS), Legendary/Mythic 80 tick (4 giây), xoáy tím/halo vàng và reveal; giữ chuyển động Common/Rare. Tài liệu phân biệt ItemDisplay native với preview cá packet riêng cho admin, giữ các hợp đồng reward/probability/schema.

Ghi rõ alias `DUST`/`REDSTONE` với `Particle.DustOptions`, trần dùng chung 24 hạt riêng lẻ mỗi tick Paper thực cho native và packet cinematic stage/frame/trail, cùng giới hạn 24 hạt/frame native. Packet mặc định 12 hạt/tick; loader chấp nhận 0–24. Cue bite/catch cũ và ambient nằm ngoài bộ đếm cinematic này.

Đã thêm lệnh preview Epic/Legendary, quyền admin, yêu cầu nhìn nước, lựa chọn effects/HUD/particle/sound, cleanup và checklist client. Giữ cờ nguồn OFF, hướng dẫn giữ cờ admin-preview runtime hiện có khi triển khai, và yêu cầu restart sau thay JAR.

## Đối chiếu và xác minh

- Đọc `AGENTS.md`, tài liệu hiện có và mã nguồn `VanillaCatchCinematicService`, `PremiumCinematicTimeline`, `CinematicParticlePattern`, `CinematicParticleEffects`, `CinematicParticleBudget`, `PacketCatchCinematicService`, `SimulatorEffectsService`, `PremiumCinematicSettings`, `PremiumCinematicConfigLoader`, `SimulatorModule`, `SimulatorSubcommand` và `simulator/cinematics.yml`.
- Nội dung 62/80 tick, màu, quyền/lệnh, giới hạn và lifecycle được đối chiếu trực tiếp với mã nguồn/config; không thay đổi mã hoặc các tài liệu ngoài phạm vi.
- Điều phối viên báo 114 test đã pass. Build package mọi biến thể và harness PacketEvents 2.13 của bản polish vẫn đang chạy tại thời điểm cập nhật tài liệu; báo cáo này không chứng nhận kết quả của các bước đó.
- Không có script `.claude/scripts/validate-docs.cjs` trong checkout. Kiểm tra thủ công nội dung và liên kết nội bộ của các phần mới thay cho script.
- Hai tệp trong phạm vi không có whitespace cuối dòng và các liên kết tương đối đều tồn tại. `git diff --check` toàn checkout báo whitespace sẵn có tại `README.md:139`; giữ nguyên vì ngoài phạm vi.

## Giới hạn bằng chứng và bước tiếp theo

Chưa xác nhận upload, restart, render/cleanup trên client hoặc MSPT thực tế của bản polish. Điều phối viên tiếp tục build/harness, triển khai theo phạm vi được phép và ghi bằng chứng riêng; admin thực hiện checklist sau restart. Tài liệu không tuyên bố hoàn tất các cổng release Simulator.
