# API mua và bán sản phẩm

Base URL local: `http://localhost:8080/api`

Tất cả API đơn hàng yêu cầu JWT:

```http
Authorization: Bearer <accessToken>
```

## Luồng trạng thái

```text
PENDING --người bán xác nhận--> CONFIRMED --người mua đã nhận--> COMPLETED
   |                               |
   +---------- hủy đơn ------------+
                 -> CANCELLED
```

- Tạo đơn sẽ giữ sản phẩm ngay bằng cách đặt `sold=true`.
- Chỉ người bán hoặc admin được xác nhận đơn.
- Chỉ người mua hoặc admin được đánh dấu đã nhận hàng.
- Người mua, người bán hoặc admin có thể hủy đơn đang `PENDING`/`CONFIRMED`.
- Hủy đơn sẽ mở bán lại sản phẩm (`sold=false`).
- Sản phẩm có đơn đang hoạt động hoặc đã hoàn tất không thể sửa/xóa.
- Giá, phí vận chuyển, tên và ảnh sản phẩm được lưu snapshot trong đơn hàng để lịch sử không thay đổi.

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
  "note": "Gọi trước khi giao"
}
```

Với `SHIP`, `deliveryAddress` là bắt buộc. Với `MEETUP`, `meetupLocation` là bắt buộc. Response thành công là `201 Created` và chứa toàn bộ đơn hàng cùng `orderCode` dạng `PD-00001`.

## Lịch sử mua hàng

```http
GET /api/orders/purchases
```

Trả về các đơn người hiện tại đã mua, mới nhất trước.

## Lịch sử bán hàng

```http
GET /api/orders/sales
```

Trả về các đơn thuộc sản phẩm người hiện tại đã bán, mới nhất trước.

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

Giá trị hợp lệ: `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`. API sẽ từ chối chuyển trạng thái sai luồng hoặc sai vai trò bằng `403`/`409`.

## Dữ liệu demo

Khi `SEED_DATA_ENABLED=true`, hệ thống tạo sẵn hai đơn cho `buyer@pado.test`:

- Một đơn `COMPLETED` của `seller.linh@pado.test` để xem lịch sử đã mua/bán.
- Một đơn `PENDING` của `seller.minh@pado.test` để thử thao tác xác nhận, hoàn tất hoặc hủy.

