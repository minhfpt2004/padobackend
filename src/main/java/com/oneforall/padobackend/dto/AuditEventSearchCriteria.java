package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.AuditCategory;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;

import java.time.Instant;

public record AuditEventSearchCriteria(
        Long actorUserId,
        Long subjectUserId,
        String actorEmail,
        AuditCategory category,
        AuditEventType eventType,
        AuditOutcome outcome,
        AuditSource source,
        AuditTargetType targetType,
        String targetId,
        String requestId,
        Instant from,
        Instant to
) {
}
