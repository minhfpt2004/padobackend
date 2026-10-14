# Deploy PADO Backend lên Render

Backend được đóng gói bằng Docker/Java 21. File `render.yaml` tạo một Web Service gói Free tại Singapore, tự deploy khi nhánh được liên kết có commit mới và kiểm tra sức khỏe qua `/api/health`.

## 1. Chuẩn bị Supabase

Render không kết nối được direct host IPv6 dạng `db.<project-ref>.supabase.co` nếu project không có IPv4 Add-on. Với project hiện tại, vào Supabase Dashboard:

1. Chọn **Connect**.
2. Chọn **Session pooler**.
3. Sao chép đúng host `aws-...pooler.supabase.com`, port `5432` và username.

Giá trị nhập trên Render:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.hfwmxrrfxsmfhewffemc
SPRING_DATASOURCE_PASSWORD=<MAT_KHAU_DATABASE_MOI>
```

Không dùng `db.hfwmxrrfxsmfhewffemc.supabase.co` trên Render trừ khi Supabase đã bật IPv4 Add-on. Project này đã được kiểm tra thực tế với cluster `aws-1`; `aws-0` sẽ trả lỗi `tenant/user ... not found`.

Hibernate đang dùng `ddl-auto=update`, vì vậy lần khởi động đầu sẽ tự tạo/cập nhật bảng và các cột mới. Seed data vẫn được cập nhật với `SEED_DATA_ENABLED=true`, không xóa dữ liệu người dùng hiện có.

## 2. Tạo Backend Blueprint

1. Push nhánh `main` mới nhất lên repository `minhfpt2004/padobackend`.
2. Trong Render chọn **New > Blueprint**.
3. Chọn repository backend và file `render.yaml` ở thư mục gốc.
4. Nhập các biến `sync: false` bên dưới rồi tạo service.

### Biến bắt buộc

| Biến                         | Giá trị                                               |
| ---------------------------- | ----------------------------------------------------- |
| `SPRING_DATASOURCE_URL`      | JDBC URL Session pooler như trên                      |
| `SPRING_DATASOURCE_USERNAME` | `postgres.hfwmxrrfxsmfhewffemc`                       |
| `SPRING_DATASOURCE_PASSWORD` | Mật khẩu database Supabase                            |
| `CORS_ALLOWED_ORIGINS`       | Các URL frontend, phân cách bằng dấu phẩy, không có `/` cuối |
| `CORS_REQUIRED_ORIGINS`      | URL frontend production luôn được phép, mặc định là `https://pado-shop.netlify.app` |
| `ADMIN_EMAIL`                | Email admin demo                                      |
| `ADMIN_PASSWORD`             | Mật khẩu admin tối thiểu 12 ký tự                     |
| `SEED_DATA_PASSWORD`         | Mật khẩu chung cho tài khoản seed, tối thiểu 12 ký tự |
| `VIETQR_ACCOUNT_NUMBER`      | Số tài khoản nhận chuyển khoản                        |
| `VIETQR_ACCOUNT_NAME`        | Tên chủ tài khoản không dấu                           |
| `CLOUDINARY_CLOUD_NAME`      | Cloud name Cloudinary                                 |
| `CLOUDINARY_API_KEY`         | API key Cloudinary                                    |
| `CLOUDINARY_API_SECRET`      | API Secret Cloudinary, không phải API key             |

`JWT_SECRET` được Render tự sinh. Không commit `.env`, mật khẩu, Resend key hoặc Cloudinary secret vào Git.

Blueprint hiện giữ chế độ OTP demo:

```properties
EMAIL_PROVIDER=log
AUTH_OTP_EXPOSE_CODE=true
```

OTP được trả về frontend để demo MVP. Khi có domain gửi email đã xác minh, đổi sang:

```properties
EMAIL_PROVIDER=resend
AUTH_OTP_EXPOSE_CODE=false
RESEND_API_KEY=<KEY_MOI_CHI_DAT_O_BACKEND>
EMAIL_FROM=PADO <verify@ten-mien-da-xac-minh>
```

Không dùng biến `VITE_RESEND_API_KEY`: mọi biến `VITE_*` đều có thể bị đóng gói vào trình duyệt. Key Resend đã từng chia sẻ công khai nên cần thu hồi và tạo key mới trước khi dùng production.

## 3. Kiểm tra backend sau deploy

```text
https://<backend-service>.onrender.com/api/health
https://<backend-service>.onrender.com/api/products
https://<backend-service>.onrender.com/swagger-ui/index.html
```

Health check hợp lệ:

```json
{ "status": "UP", "database": "UP" }
```

Nếu deploy báo lỗi kết nối database, kiểm tra lại Session pooler host, username có hậu tố project ref và `sslmode=require`.

## 4. Nối frontend

Frontend cần:

```properties
VITE_API_BASE_URL=https://<backend-service>.onrender.com/api
```

Sau khi Render cấp URL frontend, cập nhật backend:

```properties
CORS_ALLOWED_ORIGINS=https://pado-shop.netlify.app,https://pado-shop.onrender.com
CORS_REQUIRED_ORIGINS=https://pado-shop.netlify.app
```

Có thể khai báo nhiều origin bằng dấu phẩy.
