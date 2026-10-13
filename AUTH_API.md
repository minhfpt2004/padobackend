# Authentication and registration OTP

## Registration flow

1. `POST /api/auth/register` with `{ "email": "...", "password": "..." }`.
   The backend stores a pending registration and sends a six-digit OTP. It does not create a user yet.
2. `POST /api/auth/register/verify` with `{ "email": "...", "otp": "123456" }`.
   A valid OTP creates the account and returns the normal JWT `AuthResponse`.
3. `POST /api/auth/register/resend` with `{ "email": "..." }` sends a new code after the cooldown.

OTPs expire after five minutes, have a 60-second resend cooldown, and allow five invalid attempts by default.

## Email delivery

The MVP demo uses a real OTP verification flow but returns the generated OTP to the frontend instead of sending email:

```properties
EMAIL_PROVIDER=log
AUTH_OTP_EXPOSE_CODE=true
```

The password and OTP are still hashed in the database. The account is created only after the returned OTP is verified. Do not use this mode for production.

After a sending domain is verified, enable real Resend delivery entirely from the backend:

```properties
EMAIL_PROVIDER=resend
RESEND_API_KEY=re_...
EMAIL_FROM=PADO <verify@your-verified-domain.com>
AUTH_OTP_EXPOSE_CODE=false
```

The Resend API key must never use a `VITE_` prefix or be bundled into frontend JavaScript.
