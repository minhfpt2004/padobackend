# API mua và bán sản phẩm

Base URL local: `http://localhost:8080/api`

Tất cả API đơn hàng yêu cầu JWT:

```http
Authorization: Bearer <accessToken>
```

## Luồng trạng thái

```text
PENDING --người bán chọn--> CONFIRMED --người mua đã nhận--> COMPLETED
   |                           |
   +---------- hủy đơn --------+
                -> CANCELLED
```

- Nhiều người mua khác nhau có thể tạo đơn `PENDING` cho cùng một sản phẩm.
- Một người mua chỉ có một đơn `PENDING` hoặc `CONFIRMED` cho cùng sản phẩm.
- Sản phẩm vẫn có `sold=false` khi mới có các đơn `PENDING`.
- Người bán xem danh sách ứng viên mới nhất qua `GET /api/orders/sales/pending`. Mỗi đơn có thời gian đặt và `distanceKm` nếu cả sản phẩm và người mua đều có tọa độ.
- Khi người bán chọn một đơn, đơn đó chuyển thành `CONFIRMED`, sản phẩm chuyển thành `sold=true`, và tất cả đơn `PENDING` khác của cùng sản phẩm tự chuyển thành `CANCELLED`.
- Chỉ người bán hoặc admin được chọn/xác nhận đơn. Chỉ người mua hoặc admin được đánh dấu đã nhận hàng.
- Người mua, người bán hoặc admin có thể hủy đơn đang `PENDING`/`CONFIRMED`.
- Hủy đơn `CONFIRMED` sẽ mở bán lại sản phẩm (`sold=false`).
- Sản phẩm có đơn đang hoạt động hoặc đã hoàn tất không thể sửa/xóa.
- Giá, phí vận chuyển, tên và ảnh sản phẩm được lưu snapshot trong đơn hàng.

## Tạo đơn mua hàng

```http
POST /api/orders
Content-Type: application/json

{
  "productId": 1,
  "deliveryType": "SHIP",
  "buyerName": "Nguyễn An",
  "buyerPhone": "0908123456",
  "deliveryAddress": "25 Nguyễn Huệ, Quận 1, TP.HCM",
  "meetupLocation": null,
  "note": "Gọi trước khi giao",
  "buyerLatitude": 10.773,
  "buyerLongitude": 106.704
}
```

Với `SHIP`, `deliveryAddress` là bắt buộc. Với `MEETUP`, `meetupLocation` là bắt buộc. Hai tọa độ người mua là tùy chọn nhưng phải được gửi cùng nhau. Response thành công là `201 Created` và chứa toàn bộ đơn hàng, `distanceKm` cùng `orderCode` dạng `PD-00001`.

## Lịch sử mua hàng

```http
GET /api/orders/purchases
```

Trả về các đơn người hiện tại đã đặt, mới nhất trước.

## Lịch sử bán hàng

```http
GET /api/orders/sales
```

Trả về mọi lượt đặt thuộc sản phẩm của người bán, mới nhất trước.

## Danh sách ứng viên chờ người bán chọn

```http
GET /api/orders/sales/pending
```

Trả về các đơn `PENDING` của người bán, mới nhất trước. Frontend dùng endpoint này cho huy hiệu thông báo và danh sách lựa chọn. Một đơn không còn xuất hiện sau khi được xác nhận, từ chối, hoặc tự hủy vì người bán đã chọn ứng viên khác cho cùng sản phẩm.

## Chi tiết đơn hàng

```http
GET /api/orders/{id}
```

Chỉ người mua, người bán hoặc admin có quyền xem.

## Cập nhật trạng thái

```http
PATCH /api/orders/{id}/status
Content-Type: application/json

{
  "status": "CONFIRMED"
}
```

Giá trị hợp lệ: `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`. API từ chối chuyển trạng thái sai luồng hoặc sai vai trò bằng `403`/`409`.

## Dữ liệu demo

Khi `SEED_DATA_ENABLED=true`, hệ thống cập nhật dữ liệu mẫu và giữ nguyên các đơn demo hiện có.
