# Tài khoản test MVP

> Chỉ sử dụng cho môi trường local/demo. Không sử dụng các mật khẩu này ở production.

## Danh sách tài khoản

| Vai trò | Email | Mật khẩu | Mục đích test |
|---|---|---|---|
| Admin | `admin@pado.test` | `AdminLocalPassword123!` | Quản lý người dùng và sản phẩm |
| Người bán 1 | `seller.linh@pado.test` | `DemoPassword123!` | Chủ sở hữu 10 sản phẩm; có một đơn đã hoàn tất |
| Người bán 2 | `seller.minh@pado.test` | `DemoPassword123!` | Chủ sở hữu 10 sản phẩm; có một đơn chờ xác nhận |
| Người mua | `buyer@pado.test` | `DemoPassword123!` | Có lịch sử một đơn hoàn tất và một đơn đang chờ |

## API đăng nhập

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

Ví dụ:

```json
{
  "email": "seller.linh@pado.test",
  "password": "DemoPassword123!"
}
```

JWT trả về trong trường `accessToken`. Gửi token trong các request yêu cầu đăng nhập:

```http
Authorization: Bearer <accessToken>
```

## Khởi tạo dữ liệu

Các tài khoản được tạo tự động khi `.env` có:

```properties
SEED_DATA_ENABLED=true
SEED_DATA_PASSWORD=DemoPassword123!
ADMIN_EMAIL=admin@pado.test
ADMIN_PASSWORD=AdminLocalPassword123!
```

Khởi động database và backend:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Seed có tính idempotent: khởi động lại ứng dụng không tạo trùng tài khoản, sản phẩm hoặc đơn hàng.

Chi tiết endpoint và luồng trạng thái xem tại [ORDER_API.md](ORDER_API.md).
