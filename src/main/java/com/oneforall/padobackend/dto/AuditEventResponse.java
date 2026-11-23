package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.AuditCategory;
import com.oneforall.padobackend.entity.AuditEvent;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.Role;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AuditEventResponse(
        Long id,
        AuditCategory category,
        AuditEventType eventType,
        AuditOutcome outcome,
        AuditSource source,
        Long actorUserId,
        Long subjectUserId,
        String actorEmail,
        Role actorRole,
        AuditTargetType targetType,
        String targetId,
        String requestId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata,
        String errorCode,
        Instant createdAt
) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getCategory(), event.getEventType(),
                event.getOutcome(), event.getSource(), event.getActorUserId(), event.getSubjectUserId(),
                event.getActorEmail(),
                event.getActorRole(), event.getTargetType(), event.getTargetId(), event.getRequestId(),
                event.getIpAddress(), event.getUserAgent(),
                Collections.unmodifiableMap(new LinkedHashMap<>(event.getMetadata())),
                event.getErrorCode(), event.getCreatedAt());
    }
}
