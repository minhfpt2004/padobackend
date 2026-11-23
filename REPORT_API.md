# Report and moderation API

Base URL: `http://localhost:8080/api`

All endpoints require `Authorization: Bearer <accessToken>`. Endpoints below `/admin` require role `ADMIN`.

## State machine

```text
OPEN --admin claims--> IN_REVIEW --resolve--> RESOLVED
                              └--dismiss--> DISMISSED
```

- A reporter can have only one active (`OPEN`/`IN_REVIEW`) report for the same target.
- A reporter can have at most 20 active reports.
- The admin who claims a report is the only admin allowed to complete it.
- `@Version` optimistic locking prevents silent concurrent overwrites.
- Internal notes are returned only by admin endpoints.

## Create report

```http
POST /reports
Content-Type: application/json

{
  "targetType": "PRODUCT",
  "targetId": 42,
  "reason": "COUNTERFEIT",
  "details": "Logo and serial number do not match.",
  "evidenceUrls": ["https://cdn.example.com/evidence.jpg"]
}
```

Target types: `PRODUCT`, `USER`, `CONVERSATION`, `ORDER`.

Reasons: `COUNTERFEIT`, `SCAM`, `PROHIBITED_ITEM`, `MISLEADING_INFORMATION`, `HARASSMENT`, `SPAM`, `OTHER`.

Authorization rules:

- A user cannot report their own product or their own account.
- Only a participant can report a conversation or order.
- Evidence accepts at most five HTTP(S) URLs.

Response: `201 Created` with a `ReportResponse`.

## My reports

```http
GET /reports/mine
```

Returns the signed-in reporter's reports, newest first. Admin-only `internalNote` is always omitted.

## Admin queue

```http
GET /admin/reports?status=OPEN&targetType=PRODUCT&page=0&size=20
GET /admin/reports/{id}
```

Optional filters: `status`, `targetType`, `reporterId`. Page size is capped at 100.

## Claim a report

```http
PATCH /admin/reports/{id}/claim
Content-Type: application/json

{
  "internalNote": "Checking seller evidence"
}
```

Only an `OPEN` report can be claimed. Repeating the request by the assigned admin is idempotent.

## Resolve a report

```http
PATCH /admin/reports/{id}/resolve
Content-Type: application/json

{
  "resolutionNote": "Listing violates the authenticity policy.",
  "action": "HIDE_PRODUCT"
}
```

Actions:

- `NONE`: record the result only.
- `HIDE_PRODUCT`: allowed only for product reports; removes the product from public list/detail and blocks new orders/chats.
- `WARN_USER`: sets the reported user's moderation status to `WARNED`.
- `SUSPEND_USER`: sets status to `SUSPENDED`; login and existing JWT authentication are rejected.

Administrator accounts cannot be warned or suspended through a report.

## Dismiss a report

```http
PATCH /admin/reports/{id}/dismiss
Content-Type: application/json

{
  "resolutionNote": "No policy violation was found."
}
```

## Audit events

The flow records:

- `REPORT_CREATED`
- `REPORT_CLAIMED`
- `REPORT_RESOLVED`
- `REPORT_DISMISSED`
- `PRODUCT_MODERATION_HIDDEN`
- `USER_WARNED`
- `USER_SUSPENDED`

Report contents and internal notes are not copied into audit metadata.
