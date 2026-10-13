# Membership and VietQR payment API

Base URL: `http://localhost:8080/api`.

The backend persists plans, membership state, Boost balance, and payment history. Creating a QR payment returns a VietQR image URL containing the receiving account, exact amount, and a unique transfer reference.

Production configuration:

```properties
PAYMENT_MOCK_ENABLED=false
VIETQR_BANK_ID=970436
VIETQR_BANK_NAME=Vietcombank
VIETQR_ACCOUNT_NUMBER=your_account_number
VIETQR_ACCOUNT_NAME=YOUR ACCOUNT NAME
VIETQR_TEMPLATE=compact2
```

Keep all receiving-account configuration on the backend. Mock confirmation remains available only when `PAYMENT_MOCK_ENABLED=true`, such as in automated tests.

## Public plans

`GET /membership/plans`

Returns active plans only. No authentication is required.

## Current membership

`GET /membership/me`

Returns the signed-in user's current plan, Boost balance, activation time, and expiry.

## Create a pending payment

`POST /membership/payments`

```json
{
  "planCode": "PLUS",
  "paymentMethod": "QR"
}
```

Only `QR` is available when mock mode is disabled. `FREE` cannot be purchased. The response includes `qrImageUrl`, `transferContent`, and the receiving-bank display fields. `PLUS` and `PRO` activate a membership after confirmation; `BOOST` only adds Boost credits.

## Mock confirmation (tests/local only)

`POST /membership/payments/{id}/mock-confirm`

Confirmation is idempotent. Calling it twice does not grant benefits twice.

## Admin reconciliation

`POST /admin/membership/payments/{id}/confirm`

After verifying the bank statement's amount and transfer content, an administrator confirms the payment. Confirmation is idempotent and activates the associated membership or Boost balance exactly once.

## Payment history and cancellation

- `GET /membership/payments`
- `GET /membership/payments/{id}`
- `DELETE /membership/payments/{id}` — cancels a `PENDING` payment only.

## Admin

- `GET /admin/membership/plans`
- `POST /admin/membership/plans`
- `PUT /admin/membership/plans/{code}`
- `DELETE /admin/membership/plans/{code}` — hides the plan instead of deleting payment history.
- `GET /admin/membership/payments`
- `POST /admin/membership/payments/{id}/confirm`
