# Hồ câu và cinematic cá hiếm

Cập nhật: 2026-10-18. Các tính năng này dùng luồng câu EMF thông thường; không cần bật Simulator hoặc reward bridge.

## Bảng hạng và mồi câu

`/emf top` hiển thị tên cuộc thi đang diễn ra, loại, số giây còn lại và hạng cá nhân (kể cả ngoài các dòng top).
Người có cùng điểm và cùng thời điểm bắt vẫn giữ hai dòng riêng; UUID là tiêu chí cuối cùng. Thiếu màu dòng
không cắt bảng hạng: dùng màu cuối cùng, hoặc trắng nếu danh sách trống. Help người chơi và quản trị có tiêu đề
riêng; `messages_vi.yml` và `messages_vn.yml` có mô tả lệnh bằng tiếng Việt.

Trước khi thêm, dùng hoặc gỡ mồi, EMF kiểm tra toàn bộ chuỗi số lượng. Chuỗi sai, trùng ID, số không dương,
quá giới hạn số nguyên hoặc tổng gỡ bị tràn được giữ nguyên, không xóa lore trước khi kiểm tra. Chỉ các khóa
EMF và dòng lore có dấu sở hữu của EMF được cập nhật; lore ngoài, PDC/NBT, enchant và nhận diện MMOItems
giữ nguyên. Addon MMOItems hiện có kiểm tra provider và ID, trả bản sao item thật. Simulator vẫn tắt mặc định.

## Hologram trên phao câu

Hologram TextDisplay đi theo phao, cao hơn phao 0.7 block. Khi chờ, dòng đầu là **Đang chờ cá** màu aqua; khi BITE, dòng **Hãy giật cần** có gradient và dấu chấm chuyển động ngay trên hologram. Nhánh giao diện phao không gửi `showTitle` hoặc `clearTitle`, nên giữ nguyên title của các tính năng khác. TextDisplay vẫn hiển thị trong world như trước, không phải hologram riêng cho người câu.

Chữ ở cả trạng thái chờ, cắn và hụt cùng dòng đếm đều tắt đậm, kể cả đậm kế thừa. Cá tuột do hết cửa sổ giật cần chỉ kết thúc hiệu ứng BITE; phao còn sống giữ nhận diện cần và hồ để lần cắn kế tiếp tiếp tục qua luồng bắt cá EMF bình thường. Đổi cần vẫn bị từ chối; reel/catch thật, thả lại cần và rời server vẫn dọn trạng thái.

Khi đọc được bộ đếm native, dòng thứ hai có **20 vạch cyan/xám tối** và số giây màu trắng, ví dụ `1.3s`. Chữ cập nhật mỗi **2 tick**; thanh biểu diễn thời gian còn lại của pha đang quan sát (wait, cá tiến tới hoặc cửa sổ giật cần), không phải tổng thời gian từ lúc thả đến lúc nhận cá. Hệ thống chỉ đọc bộ đếm thực, không đổi đồng hồ câu, Lure, ưu đãi hồ hay phần thưởng.

Bộ đếm BITE được lấy ở tick sau sự kiện vì native khởi tạo cửa sổ giật cần sau callback. Nếu đọc được cửa sổ BITE bằng 0, hologram trở về trạng thái chờ, kể cả khi listener khác hủy BITE muộn. Mapping thiếu hoặc không truy cập được sẽ **bỏ dòng đếm số**, không đoán thời gian. Trên legacy 1.20.1 chỉ getter wait được bảo đảm; mapping native có tên chính xác `timeUntilHooked`/`nibble` được hỗ trợ trên bản hiện đại 1.21.11. Số giây quy đổi từ tick theo 20 TPS; thời tiết và TPS có thể làm thời gian thực khác con số hiển thị.

Rời server, người câu đổi sang world khác phao, phao/display không còn hợp lệ, reel/catch/in-ground hoặc disable sẽ dọn display; updater dừng khi không còn hologram. Thay JAR cần **restart server**, không cần đổi cấu hình runtime cho cập nhật này. Sau restart cần kiểm tra trực tiếp trạng thái chờ/BITE, dòng đếm, title của tính năng khác và cleanup trên client; tài liệu này không chứng nhận hình ảnh client đã được kiểm thử.

## Quản lý hồ câu

Quyền: `emf.admin` và `emf.admin.pool`. Tạo hồ trong game, nhìn vào mặt nước trong phạm vi 32 block; nếu không nhắm nước, hệ thống dùng vị trí người tạo.

```text
/emf admin pool create <id> <radius> [speed-percent] [rare-multiplier]
/emf admin pool
/emf admin pool remove <id>
/emf admin pool list
/emf admin pool info <id>
/emf admin reload
```

Ví dụ: `/emf admin pool create ho-rung 12 35 2` tạo hồ bán kính 12 block, giảm 35% thời gian chờ còn lại và nhân trọng số rarity được chọn lên x2. ID dùng 1–48 chữ cái ASCII, chữ số, `_` hoặc `-`; ID không phân biệt hoa/thường. Radius từ 1 đến 64; speed-percent từ 0 đến 85; rare-multiplier từ 1 đến 10. Bỏ hai tham số cuối để dùng mặc định 25% và x1.5.

Ba dạng tạo hồ đều được đăng ký trên các biến thể 1.20, 1.21 và 26.1:

| Lệnh | Giá trị áp dụng |
|---|---|
| `/emf admin pool create ho-rung 12` | Radius 12, speed và multiplier lấy từ mặc định cấu hình. |
| `/emf admin pool create ho-rung 12 35` | Radius 12, giảm chờ 35%, multiplier lấy từ mặc định cấu hình. |
| `/emf admin pool create ho-rung 12 35 2` | Radius 12, giảm chờ 35%, trọng số rarity phù hợp x2. |

Hai tham số tùy chọn theo thứ tự; muốn đặt multiplier phải nhập speed trước. `default-speed-percent` và `default-rare-multiplier` trong `fishing-pools.yml` chỉ áp dụng khi tạo hồ mới mà bỏ tham số tương ứng. Không có lệnh sửa hồ: dùng `remove` rồi `create`, hoặc sửa YAML và reload. `create` cần người chơi; `list`, `info`, `remove` dùng được từ console có quyền. ID đã tồn tại, ID sai định dạng, giá trị lệnh ngoài giới hạn hoặc vượt 256 hồ sẽ bị từ chối.

`fishing-pools.yml` mặc định bật tính năng với danh sách hồ trống. Lệnh tạo/xóa lưu ngay bằng thay thế file nguyên tử; restart không xóa hồ. Có tối đa 256 hồ. Nếu YAML sai kiểu, chứa số không hữu hạn hoặc ID trùng sau khi bỏ phân biệt hoa/thường, reload giữ cấu hình hợp lệ trước đó. Các giá trị hữu hạn ngoài giới hạn được đưa về giới hạn hỗ trợ.

Ưu đãi dựa trên phao đã chạm nước, đúng world, khoảng cách ngang nằm trong radius và lệch độ cao không quá `vertical-tolerance` (mặc định 2 block). Vị trí người câu không thay thế vị trí phao. Hai hồ giao nhau chọn tâm gần nhất, rồi ID theo thứ tự chữ cái nếu bằng nhau; ưu đãi không cộng dồn.

Kiểm tra nước nhận cả cỏ biển, tảo bẹ, cột bong bóng và block có `waterlogged=true`; lava, block khô và chunk chưa tải không nhận ưu đãi, đồng thời không đọc block ngoài chiều cao world. Luồng câu native không dùng `simulator.ambient-preview.minimum-water-depth`; độ sâu tối thiểu của bộ quét preview không phải điều kiện phát cá EMF.

Giảm chờ áp dụng một lần mỗi lần thả cần, trên bộ đếm đã bao gồm Lure và điều chỉnh của cần custom/buff. Phao rời hồ trước khi cá bắt đầu tiến tới sẽ lấy lại phần thời gian bị giảm, giữ thời gian đã trôi qua. Vào lại hồ trong cùng lần thả không giảm thêm. Bán kính được đánh dấu bằng vòng hạt chỉ gửi cho người xem gần đó; không tải chunk hoặc tạo entity lâu dài.

`rare-rarity-ids` là danh sách ID chính xác, không phân biệt hoa/thường; mặc định gồm `Uncommon`, `Rare`, `Epic`, `Legendary`, `Mythical`. `Common` luôn bị loại, kể cả khi thêm vào danh sách. Hệ số nhân trọng số không phải xác suất bắt cá được bảo đảm: x2 trọng số Rare với Common=10/Rare=10 đổi tỷ lệ Rare từ 50% thành khoảng 66.7%. Phép nhân của hồ giữ trọng số 0 ở 0; điều kiện cá/rarity, bait, cần câu, vùng và cuộc thi vẫn áp dụng. Cơ chế chọn EMF cũ vẫn có fallback đồng đều nếu toàn bộ trọng số hiệu dụng đều bằng 0. Hồ không tăng tỷ lệ săn cá ngoài luồng câu cần EMF.

## Hồ T10 động từ LeDatIslandSSB — 2026-10-12

EMF hỗ trợ Bukkit service `com.oheers.fish.api.fishing.FishingLakeProvider` với `resolve(Player, Location)` trả `Bonus(id, speedPercent, rareMultiplier, hookedVisual)` hoặc `null`. API đóng gói trong EMF; plugin Island chỉ dùng compile-only. ServicesManager chọn registration ưu tiên cao nhất đang hoạt động; bonus động hợp lệ được ưu tiên trước hồ tĩnh và không cộng/nhân dồn. Provider thiếu, tắt, lỗi hoặc trả `null` dùng lại hồ tĩnh/luồng câu cũ. EMF tra service trực tiếp nên hỗ trợ đăng ký/gỡ service sau enable/disable/reload.

LeDatIslandSSB cấp bonus cho thành viên hiện tại, đảo đủ Tier và phao ở **zone đã cấu hình với requiredTier >=10**, đúng world/nước. Core/vùng Tier thấp không thành hồ T10 chỉ vì đảo lên Tier. Mặc định Island: `evenmorefish.enabled: true`, `minimum-tier: 10`, `speed-percent: 25`, `rare-multiplier: 2.0`, `hooked-visual: true` trong `modules/afk/config.yml`; thiếu khóa vẫn dùng mặc định, Tier ít nhất 10, speed clamp 0–85 và multiplier clamp 1–10. Tất cả vẫn cần `fishing-pools.enabled` của EMF bật. Cần/quyền/toggle/world-region/bait/competition/AFK policy của EMF giữ nguyên; SuperAFK độc lập. X2 là trọng số rarity trong `rare-rarity-ids`, không bảo đảm bắt Rare, không nhân số cá và không tăng Common. Không cần tạo hồ tĩnh trùng hồ Island.

Khi có bonus `hookedVisual=true`, native **BITE** đã qua chính sách EMF và không bị hủy tại MONITOR có thể mở một cá **COD trang trí riêng cho người câu** gần phao. Không có cá ambient trước BITE; cá trang trí không dự đoán phần thưởng, không đi vào catalog/population/AI/reward bridge. Một lần thả chỉ mở một lần; tối đa 16 cảnh đồng thời, 40 tick/cảnh, cập nhật mỗi 2 tick và người câu cách phao tối đa 32 block. Effects/HUD opt-out và `reduced-motion` được tôn trọng. Reel/catch/fail/in-ground, recast, quit, đổi world, mất điều kiện hồ, reload/disable hoặc hết hạn dọn cảnh. Ticker dừng khi không còn cảnh và cleanup chờ; cleanup lỗi dùng hàng đợi giới hạn.

Native renderer chỉ có optional PacketEvents transport trên biến thể **1.21**; không cần bật Simulator. Compile dependency hiện tại là 2.14; PacketEvents **2.13 đang cài ở đích** đã qua kiểm tra signature và harness runtime cô lập, còn render/cleanup client thật cần xác nhận sau restart. Thiếu/lỗi transport bỏ qua hình ảnh, vẫn giữ ưu đãi hồ và catch EMF thông thường. Giữ `simulator.enabled`, `ambient-preview.enabled`, `ai-preview.enabled`, `native-hook-probe.enabled`, `reward-bridge.enabled`, `reward-bridge.paper-gates-verified` ở `false` và các zone Simulator tắt; full Simulator/public reward bridge/population vẫn chưa kích hoạt.

Để tắt riêng hình ảnh, đặt Island `evenmorefish.hooked-visual: false`; để tắt bonus Island, đặt `evenmorefish.enabled: false`, rồi `/islandssb admin reload`. Tắt `fishing-pools.enabled` rồi `/emf admin reload` sẽ tắt cả hồ tĩnh và hồ động. Thay JAR cần clean restart, không chỉ config reload. Release hồ T10 không upload YAML runtime; các khóa mới có fallback mặc định.

Xem [hướng dẫn vận hành Island](../../../Skyblock/LeDatIslandSSB/docs/operations/fishing-lake-evenmorefish.md) cho gate Tier/zone, build API override, rollback và checklist Paper/client/MSPT. Build/test và SFTP readback không thay bằng chứng startup, render hoặc tải server thực tế.

## Cinematic cá hiếm

`config.yml` → `animations.rare-catch.cinematic` điều khiển cảnh riêng cho người câu sau khi vật phẩm câu thông thường đã được trao hoặc thay thế và sự kiện Paper không bị hủy ở bước MONITOR. Cảnh dùng nội suy vị trí/scale, không điều khiển camera hoặc phát thêm phần thưởng. Mỗi cảnh dùng một ItemDisplay tạm, không lưu vào world; chỉ người câu được cho xem entity này.

| Rarity native | Thời lượng ở 20 TPS | Hình ảnh |
|---|---|---|
| Rare và rarity khác đủ điều kiện | 26 tick, khoảng 1.3 giây | Giữ chuyển động ngắn hiện có. |
| ID chứa `epic` | 62 tick, khoảng 3.1 giây | Xoáy tím quanh mặt nước, cá nổi lên rồi xoay khi reveal trước khi bay về người câu. |
| ID chứa `legend` hoặc `myth` | 80 tick, khoảng 4 giây | Halo vàng, vòng xoáy rộng hơn và reveal dài hơn trước khi bay về người câu. |

Chuyển động Common/Rare hiện có không đổi; Common vẫn không mở cinematic native. Thời gian thực có thể dài hơn khi TPS giảm. Đây chỉ là thay đổi trình diễn Epic/Legendary/Mythic; không đổi phần thưởng, xác suất rarity hay schema dữ liệu.

Hạt màu chỉ gửi cho người xem, dùng `Particle.DustOptions` với alias `DUST`/`REDSTONE` theo phiên bản Paper. Cinematic native và stage/frame/trail của cinematic packet dùng chung trần cứng **24 hạt riêng lẻ trên mỗi tick Paper thực**, tính gộp mọi cảnh/người xem; một frame native cũng không vượt 24 hạt. Cấu hình packet `simulator/cinematics.yml` → `limits.particle-emissions-per-tick` mặc định **12 hạt/tick**, trong phạm vi loader cho phép 0–24. Cue bite/catch cũ, ambient và các hiệu ứng ngoài cinematic không thuộc bộ đếm này; đây không phải giới hạn toàn bộ particle của plugin.

Điều kiện rarity áp dụng theo thứ tự:

1. ID `Common` luôn bị loại, không phân biệt hoa/thường, kể cả khi nằm trong `rarities` hoặc có index/trọng số đủ điều kiện.
2. ID nằm trong `excluded-rarities` bị loại trước mọi điều kiện còn lại.
3. Các rarity còn lại đủ điều kiện nếu ID nằm trong `rarities`, **hoặc** sort-index từ 3 trở lên, **hoặc** trọng số hữu hạn lớn hơn 0 và không quá 30. Vì vậy `rarities` không phải allowlist tuyệt đối; muốn chặn một rarity custom hãy thêm ID vào `excluded-rarities`. Danh sách `rarities` trống dùng lại mặc định `Rare`, `Epic`, `Legendary`, `Mythical`.

`animations.rare-catch.enabled` là công tắc chung cho hiệu ứng bắt cá hiếm; `cinematic.enabled` bật riêng cảnh chuyển động. `animations.rare-catch.title-enabled` mặc định `false` để không che tầm nhìn bằng title lớn. Khi cảnh không chạy do cấu hình, cooldown, giới hạn hoặc lựa chọn cá nhân, hệ thống dùng hiệu ứng cá hiếm cũ theo các lựa chọn âm thanh/hạt/title hiện có. Cả hai đường đều áp dụng bộ lọc rarity trên.

`cooldown-ticks` mặc định 60 (3 giây, giới hạn 0–1200) và `max-concurrent` mặc định 8 (giới hạn 1–32) giới hạn số cảnh. Cảnh không mở khi người câu cách vị trí phao quá 32 block, khác world hoặc chunk phao chưa tải. Thả cần mới, đổi world, rời server, reload hoặc disable dọn cảnh; di chuyển hơn 3 block từ vị trí mở cảnh cũng dừng cảnh. Cả native và packet tôn trọng lựa chọn effects, HUD, particle và sound cá nhân: tắt particle/sound không phát thành phần tương ứng; effects opt-out hoặc HUD không cho phép cinematic sẽ chặn/dọn cảnh.

Trên biến thể **1.21**, `/emf hud <off|minimal|balanced|cinematic|reduced-motion>` được đăng ký với quyền `emf.simulator.preferences`. `balanced` và `cinematic` cho phép cảnh; `off`, `minimal`, `reduced-motion` không cho phép cảnh. Lệnh `/emf hud` mở GUI nếu cấu hình GUI hiện có cho phép, nếu không hiển thị preset hiện tại. Không dùng lệnh này để bật Simulator. Biến thể 1.20 và 26.1 chưa đăng ký lệnh HUD này trong adapter hiện tại.

## Preview cinematic Epic/Legendary cho admin

Trên adapter **1.21**, admin có quyền `emf.admin.simulator.preview` nhìn vào nước trong phạm vi 12 block rồi chạy:

```text
/emf admin simulator cinematic preview epic
/emf admin simulator cinematic preview legendary
/emf admin simulator cinematic stop
```

Preview dùng cá packet trang trí riêng cho admin, không trao vật phẩm hoặc đổi catalog. Preset nguồn Epic/Legendary có cùng thời lượng 62/80 tick và hiệu ứng tím/vàng ở trên; cấu hình runtime tùy chỉnh có thể khác. Common/Rare giữ preset chuyển động cũ. Packet cleanup dùng hàng đợi retry nếu gửi destroy thất bại; recast/cancel, quit, đổi world, reload/disable hoặc hết cảnh sẽ dọn theo lifecycle hiện có.

Preview thuộc nhánh riêng, cần admin-preview Simulator/ambient đang chạy, transport PacketEvents khả dụng, effects/cinematic/premium preview đã được cấu hình và lựa chọn HUD/effects cho phép cảnh. Các điều kiện preview này không bắt buộc với cinematic native. `simulator/cinematics.yml` cùng các cờ Simulator/preview trong nguồn vẫn **OFF mặc định**. Khi triển khai bản polish, giữ các cờ admin-preview runtime đã có, không bật thêm public Simulator hoặc reward bridge. Thay JAR phải **restart server** để nạp mã mới; `/emf admin reload` chỉ nạp lại cấu hình, không thay thế restart.

Sau restart, kiểm tra riêng hai preset, màu/xoáy/reveal, tính riêng tư với người đứng cạnh, particle/sound opt-out, HUD `off`/`minimal`/`reduced-motion` và cleanup khi recast/quit/reload. Chưa có bằng chứng client thật cho bản polish chỉ từ test/build/harness; không suy ra MSPT thực tế từ benchmark packet cô lập.

## Tắt và kiểm tra thực tế

Để tắt ưu đãi hồ, đặt `fishing-pools.enabled: false` trong `fishing-pools.yml`, rồi `/emf admin reload`; định nghĩa hồ vẫn được lưu. Để tắt cinematic, đặt `animations.rare-catch.cinematic.enabled: false` trong `config.yml`, rồi reload. Không thay đổi các cờ Simulator để sử dụng hai tính năng này.

Để chỉ tắt vòng radius, đặt `fishing-pools.particles.enabled: false`. Để tắt cả cảnh lẫn hiệu ứng cá hiếm cũ, đặt `animations.rare-catch.enabled: false`; để bật lại title cũ, đặt `animations.rare-catch.title-enabled: true`. Reload hợp lệ khôi phục timer/Lure của phao đang được theo dõi và dọn các cảnh trước khi dùng cấu hình mới. Sao lưu YAML trước khi sửa; reload YAML hồ lỗi giữ trạng thái hợp lệ trước đó và ghi cảnh báo.

Unit test và build cục bộ không thay thế kiểm thử Paper/client. Trên server thử nghiệm cần xác nhận: tạo/xóa/restart giữ dữ liệu; phao trong/ngoài hồ, sai tầng và hồ giao nhau; cần Lure/custom rod/buff; vòng radius nhìn từ client; Common không có cinematic; cá hiếm có cảnh mượt; thả cần mới/quit/reload/disable dọn sạch cảnh. Hình ảnh thực tế và tốc độ câu trên Paper vẫn cần được xác nhận trực tiếp.

Kết quả kiểm tra cục bộ và các giới hạn bằng chứng được ghi trong [báo cáo xác minh](../plans/20261012-vanilla-cinematics-fishing-pools/reports/final-verification.md). Các báo cáo Simulator lịch sử trong README vẫn là bằng chứng staging; tính năng câu thông thường này không hoàn tất các cổng phát hành Simulator hay thay thế signoff thực tế.


## Hiệu ứng native và Auto Câu Cá (2026-10-29)

Hạt lúc cá cắn chỉ gửi cho người câu, sau khi sự kiện BITE được chấp nhận ở MONITOR. Tôn trọng lựa chọn effects/particle cá nhân, tối đa 19 hạt mỗi lần cắn. BITE bị hủy không phát hiệu ứng. Common luôn bỏ qua cinematic, kể cả khi bật all-rarities. Simulator và reward bridge tiếp tục tắt.

Cảnh native mặc định dùng đúng bản xem trước của vật phẩm đã trao trong cả pha chuyển động và reveal. nimations.rare-catch.cinematic.candidate-preview: true cho phép xem lại các mẫu cá trang trí; material-wheel là lựa chọn riêng và mặc định tắt. Hai lựa chọn này không chọn hoặc trao thêm phần thưởng. Auto Câu Cá chỉ tăng số cá bắt và chuỗi tập trung khi CAUGHT_FISH được chấp nhận, không tăng khi chỉ giật cần thành công, câu thực thể hoặc sự kiện bị hủy.
