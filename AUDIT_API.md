# Admin Audit

## Audit Console

The backend serves a standalone admin dashboard at:

```text
/admin/audit/
```

The page itself is public so it can render the login form. All audit data APIs remain protected by
`ROLE_ADMIN`. The console stores the JWT in `sessionStorage` and removes it on logout or HTTP 401.

Features:

- paginated event ledger;
- filters for actor/customer, category, event type, outcome, source, request ID and time range;
- event detail drawer with request context and metadata;
- customer timeline shortcut via `subjectUserId`;
- responsive desktop/mobile layout.

## Admin APIs

All endpoints require `Authorization: Bearer <admin-token>`.

### Search events

```http
GET /api/admin/audit-events?page=0&size=20
```

Optional filters:

```text
actorUserId, subjectUserId, actorEmail, category, eventType, outcome, source,
targetType, targetId, requestId, from, to
```

`from` and `to` use ISO-8601 timestamps. `size` is limited to 1–100.

### Event detail

```http
GET /api/admin/audit-events/{id}
```

### Export filtered events

```http
GET /api/admin/audit-events/export?category=ORDER&from=2026-01-01T00:00:00Z
```

The export accepts the same optional filters as event search and returns a UTF-8 CSV file. Every
cell is quoted and spreadsheet formula prefixes are neutralized. Response headers expose the result:

```text
X-Audit-Export-Rows
X-Audit-Export-Truncated
```

The maximum number of exported rows is controlled by `AUDIT_EXPORT_MAX_ROWS` (default `10000`,
accepted range `1`–`100000`). An `AUDIT_EXPORTED` event records the admin, filters, row count and
whether the result was truncated.

### Customer timeline

```http
GET /api/admin/users/{userId}/audit-events?page=0&size=20
```

The timeline includes events performed by the customer and events whose `subjectUserId` is that
customer, such as seller/admin actions affecting their order or membership.

### Filter options

```http
GET /api/admin/audit-events/filter-options
```

Returns the supported category, event type, outcome, source and target type values.

## Retention

Automatic retention is disabled by default so deployment cannot remove history unexpectedly. Enable
it only after the required legal and operational retention period has been agreed:

```text
AUDIT_RETENTION_ENABLED=true
AUDIT_RETENTION_DAYS=365
AUDIT_RETENTION_CRON=0 30 3 * * *
```

The cron is evaluated in UTC. When enabled, the scheduler bulk-deletes events older than the configured
number of days. A successful purge that removed at least one row creates an
`AUDIT_RETENTION_PURGED` system event containing the cutoff, retention period and deleted row count.
