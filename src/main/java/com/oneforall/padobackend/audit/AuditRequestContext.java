package com.oneforall.padobackend.audit;

import java.time.Instant;

public record AuditRequestContext(
        String requestId,
        String ipAddress,
        String userAgent,
        String httpMethod,
        String requestPath,
        Instant startedAt
) {
}
