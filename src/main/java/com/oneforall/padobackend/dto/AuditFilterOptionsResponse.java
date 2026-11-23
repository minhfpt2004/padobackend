package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.AuditCategory;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;

import java.util.List;

public record AuditFilterOptionsResponse(
        List<AuditCategory> categories,
        List<AuditEventType> eventTypes,
        List<AuditOutcome> outcomes,
        List<AuditSource> sources,
        List<AuditTargetType> targetTypes
) {
    public static AuditFilterOptionsResponse all() {
        return new AuditFilterOptionsResponse(
                List.of(AuditCategory.values()),
                List.of(AuditEventType.values()),
                List.of(AuditOutcome.values()),
                List.of(AuditSource.values()),
                List.of(AuditTargetType.values())
        );
    }
}
