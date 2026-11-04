# Title và số hàng của GUI

Cập nhật 2026-10-14. Bản đang dùng đọc `guis.yml`; các file `gui/*.yml` còn lại trên server không thay thế file này.

Mỗi menu lấy kích thước từ `layout`. Với inventory rương, mỗi dòng là một hàng, tối đa 6 hàng. Có thể khai báo `rows` để kiểm tra cấu hình, nhưng giá trị phải khớp số dòng layout; khóa này không kéo giãn menu. Bỏ khóa để giữ cách suy ra từ layout hiện tại.

`inventory-title` là khóa tùy chọn ghi đè `title` cũ. `{rows}` được thay trước khi đọc MiniMessage; các glyph nền `row_1` tới `row_6` trong header cũng được chỉnh theo layout đã resolve. Tên item, lore và Simulator không dùng quy tắc này. Layout dispenser/hopper giữ cách hiển thị hiện tại.

```yaml
main-menu:
  inventory-title: '<white><shift:-8><glyph:row_{rows}><shift:-120><dark_gray>Câu Cá'
  # layout hiện hữu vẫn quyết định số hàng và vị trí nút.
```

Chỉ Skyblock và Survival được cấu hình title có glyph trong đợt triển khai này. Các server khác dùng title thường. Giá trị `shift` và phần chữ do quản trị viên chọn được giữ nguyên. EMF hiện không có Dialog cho các menu này.

Các menu mặc định: main 6 hàng, bán cá 4, xác nhận bán 4, mồi 5, gắn mồi 4, nhật ký cá 6 và nhật ký độ hiếm 6. Khi chỉnh layout thực tế, header theo số hàng mới; không dùng bảng mặc định thay cho cấu hình đang chạy.

Kiểm tra hiển thị bằng client sau restart vẫn cần thiết để xác nhận resource pack và plugin Nexo đang hoạt động.
