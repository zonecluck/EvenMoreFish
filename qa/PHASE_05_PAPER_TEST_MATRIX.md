# Phase 04–05 — Paper / PacketEvents Acceptance Matrix

> Cần chạy trên server test riêng có Paper và PacketEvents; **chưa có kết quả runtime**.

| ID | Ưu tiên | Bài test trên server | Kết quả mong đợi |
|---|---|---|---|
| P45-01 | P0 | Gradle test + shadowJar đầy đủ trên Java 21 | Không compile errors, plugin.yml variant Paper 1.21 có `softdepend: [packetevents]`, không shade PacketEvents |
| P45-02 | P0 | Boot server không có PacketEvents | EMF chạy bình thường; preview fail-closed, không spam errors |
| P45-03 | P0 | Boot server có PacketEvents 2.14.0 | NoClassDefFoundError/LinkageError không xuất hiện; plugin enables |
| P45-04 | P0 | `simulator.enabled=false` | Không có packet preview; câu và thưởng EMF legacy nguyên vẹn |
| P45-05 | P0 | `ambient-preview.enabled=false` | Không spawn virtual fish, không đăng ký reward mới |
| P45-06 | P0 | Bật preview, chỉ admin có permission ở zone hợp lệ | Admin thấy cá packet sau khi scan nước xong |
| P45-07 | P0 | Player thường đứng cạnh admin | Không thấy bất kỳ preview packet fish nào |
| P45-08 | P0 | Hai admin cùng zone | Shared fish đồng bộ vị trí, không có ghost/double entity |
| P45-09 | P0 | Spawn private cá cho admin A | B không nhìn thấy private fish của A, dù cùng vị trí |
| P45-10 | P0 | Player chuyển world / quit / respawn | Cá ảo client cũ được dọn, không ghost/chồng ID |
| P45-11 | P0 | Reload simulator.yml hoặc lệnh `off` | Destroy packet dọn đàn cá và session |
| P45-12 | P0 | Chunk unload từ xa | Agent dọn; không force-load chunk trở lại |
| P45-13 | P0 | Đặt/phá block, đổ/xúc nước | Water index invalidation; cá không bơi vào khối đặc |
| P45-14 | P0 | `PlayerFishEvent` trong vùng preview | Không double EXP/item, vẫn chạy legacy EMF, không đổi event reward |
| P45-15 | P0 | Shop, competition, bait, rod ở ngoài vùng | Hành vi EMF hiện có không bị regression |
| P45-16 | P1 | Zone `WORLD`, `CUBOID`, WorldGuard, RedProtect | Preview chỉ ở zone được enable, deny-priority luôn thắng |
| P45-17 | P1 | Zone bị thiếu provider / sai ID | Không render ở zone không xác định, log không spam |
| P45-18 | P1 | Quyền admin bị thu hồi trong khi xem cá | Cá biến mất trong một số tick, không thể tạo cá mới |
| P45-19 | P1 | Viewer đến giới hạn / view distance | Không vượt số viewer và agent/visible caps |
| P45-20 | P1 | 160 cá, 8 viewer, packet burst | Không vượt ngân sách 800 packet/tick; profiler đo thực tế |
| P45-21 | P1 | 1.21.4, 1.21.11 | Spawn/move/destroy/metadata không crash, cá bơi trong nước |
| P45-22 | P1 | PacketEvents reload/disable bất thường | Preview fail-closed; EMF legacy không ảnh hưởng |
| P45-23 | P1 | Invalid YAML `simulator/visuals.yml` | Warning, fallback vanilla, không crash |
| P45-24 | P1 | Invalid `simulator.yml` | Atomic reload giữ config/session cũ, log key sai |
| P45-25 | P1 | `visuals.yml` có custom ITEM_DISPLAY | Warning và fallback COD/model vanilla, không tự nhận đã hỗ trợ custom model |
| P45-26 | P1 | Hồ cạn hơn depth config, đáy không có nước | Không spawn fish ở cell không hợp lệ |
| P45-27 | P1 | Hồ có dòng chảy mạnh nhiều BlockFromToEvent | Debounce ngăn quét lại vô hạn, không lag spike đáng kể |
| P45-28 | P1 | `/emf admin simulator status` en/vi/vn | Các placeholder agents/viewers/packets có số hợp lệ |
| P45-29 | P1 | Disable/re-enable plugin, join/quit liên tục | Không leak task/virtual IDs, không phát spam log |
| P45-30 | P0 | Gate G3 thử nghiệm admin (riêng Phase03) | Chặn loot vanilla đã chứng minh bằng test thực tế trước khi triển khai Phase 06/07 |

## Gợi ý profiler

- `spark profiler` và timings/packet inspector; đo TPS/MSPT, tổng packet/s, CPU scan nước, max resident agents, số người xem.
- Thử hồ trống, hồ nước động, nhiều vùng chồng, chunk unload liên tục.
- So sánh bật/tắt preview trên cùng server test, không suy ra TPS/MSPT từ headless smoke.

## Khi Gate không qua

Dừng rollout, chuyển `simulator.ambient-preview.enabled: false`, giữ `simulator.dependency.on-unavailable: LEGACY_EMF`; đính kèm server log, Paper version, PacketEvents version, profiler và bước tái hiện.
