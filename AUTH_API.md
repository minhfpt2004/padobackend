# Authentication and registration OTP

## Registration flow

1. `POST /api/auth/register` with `{ "email": "...", "password": "..." }`.
   The backend stores a pending registration and sends a six-digit OTP. It does not create a user yet.
2. `POST /api/auth/register/verify` with `{ "email": "...", "otp": "123456" }`.
   A valid OTP creates the account and returns the normal JWT `AuthResponse`.
3. `POST /api/auth/register/resend` with `{ "email": "..." }` sends a new code after the cooldown.

OTPs expire after five minutes, have a 60-second resend cooldown, and allow five invalid attempts by default.

## Email delivery

Real delivery uses Resend entirely from the backend, so the API key is never bundled into frontend JavaScript:

```properties
EMAIL_PROVIDER=resend
RESEND_API_KEY=re_...
EMAIL_FROM=PADO <verify@your-verified-domain.com>
```

For local development without provider credentials:

```properties
EMAIL_PROVIDER=log
AUTH_OTP_EXPOSE_CODE=true
```

`log` writes the OTP to the backend console. `AUTH_OTP_EXPOSE_CODE` must stay `false` outside local/test environments.
