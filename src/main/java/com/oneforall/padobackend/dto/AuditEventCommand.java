package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.Role;

import java.util.Map;

public record AuditEventCommand(
        AuditEventType eventType,
        AuditOutcome outcome,
        AuditSource source,
        Long actorUserId,
        String actorEmail,
        Role actorRole,
        AuditTargetType targetType,
        String targetId,
        String requestId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata,
        String errorCode
) {
}
