# Deploy PADO Backend lên Render

Backend được đóng gói bằng Docker/Java 21 và triển khai bằng Render Blueprint tại `render.yaml`.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/minhfpt2004/padobackend)

## 1. Đẩy source lên GitHub

Đảm bảo các file `Dockerfile`, `.dockerignore`, `render.yaml` và source mới nhất đã có trên nhánh `main` của repository backend.

Không commit file `.env`. Các secret thật chỉ nhập trong Render Dashboard.

## 2. Tạo Blueprint

1. Mở Render Dashboard.
2. Chọn **New > Blueprint**.
3. Kết nối repository `minhfpt2004/padobackend`.
4. Render đọc `render.yaml`, tạo web service `pado-backend` tại Singapore.
5. Nhập các biến được Render yêu cầu.

## 3. Biến môi trường cần nhập

| Biến | Giá trị cần dùng |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL của Supabase **Session pooler**, gồm `sslmode=require` và `currentSchema=pado` |
| `SPRING_DATASOURCE_USERNAME` | Username Session pooler, thường có dạng `postgres.<project-ref>` |
| `SPRING_DATASOURCE_PASSWORD` | Password database Supabase |
| `CORS_ALLOWED_ORIGINS` | Origin frontend production, không có dấu `/` cuối URL; có thể phân tách nhiều origin bằng dấu phẩy |
| `ADMIN_EMAIL` | Email admin bootstrap |
| `ADMIN_PASSWORD` | Mật khẩu admin mạnh, tối thiểu 12 ký tự |
| `SEED_DATA_PASSWORD` | Mật khẩu của các tài khoản seed, tối thiểu 12 ký tự |
| `VIETQR_ACCOUNT_NUMBER` | Số tài khoản nhận thanh toán |
| `VIETQR_ACCOUNT_NAME` | Tên chủ tài khoản không dấu |
| `RESEND_API_KEY` | API key Resend phía server |
| `EMAIL_FROM` | Người gửi đã được Resend cho phép, ví dụ `PADO <verify@domain.com>` |
| `CLOUDINARY_CLOUD_NAME` | Cloud name Cloudinary |
| `CLOUDINARY_API_KEY` | API key Cloudinary |
| `CLOUDINARY_API_SECRET` | API secret Cloudinary |

`JWT_SECRET` được Render tự sinh và không cần nhập thủ công.

### Kết nối Supabase bắt buộc dùng Session pooler

Render không hỗ trợ kết nối outbound IPv6, còn direct host `db.<project-ref>.supabase.co` của Supabase mặc định là IPv6. Không dùng direct JDBC URL đang dùng ở máy local cho Render.

Trong Supabase Dashboard:

1. Mở project, chọn **Connect**.
2. Chọn **Session pooler**.
3. Lấy host, port `5432` và username mà Supabase hiển thị.
4. Nhập URL trên Render theo dạng dưới đây; không đặt password trực tiếp trong URL:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require&currentSchema=pado
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=<database-password>
```

## 4. Kiểm tra sau deploy

Thay `<backend-domain>` bằng domain Render cấp:

```text
https://<backend-domain>/api/health
https://<backend-domain>/api/products
https://<backend-domain>/swagger-ui/index.html
```

Health check thành công trả về:

```json
{"status":"UP","database":"UP"}
```

## 5. Nối frontend production

Đặt biến môi trường khi deploy frontend:

```properties
VITE_API_BASE_URL=https://<backend-domain>/api
```

Sau khi biết domain frontend, cập nhật `CORS_ALLOWED_ORIGINS` trên Render bằng đúng origin đó rồi redeploy backend. Ví dụ:

```text
https://pado-shop.example.com
```
