package com.oneforall.padobackend.service;

import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.repository.AuditEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class AuditRetentionService {
    private final AuditEventRepository events;
    private final AuditService auditService;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public AuditRetentionService(AuditEventRepository events, AuditService auditService,
                                 @Value("${app.audit.retention.days:365}") int retentionDays) {
        this(events, auditService, retentionDays, Clock.systemUTC());
    }

    AuditRetentionService(AuditEventRepository events, AuditService auditService,
                          int retentionDays, Clock clock) {
        if (retentionDays < 1) throw new IllegalArgumentException("Audit retention days must be positive");
        this.events = events;
        this.auditService = auditService;
        this.retentionDays = retentionDays;
        this.clock = clock;
    }

    @Transactional
    public int purgeExpiredEvents() {
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = events.deleteCreatedBefore(cutoff);
        if (deleted > 0) {
            auditService.recordSystemSuccess(AuditEventType.AUDIT_RETENTION_PURGED,
                    AuditTargetType.SYSTEM, "audit-events", Map.of(
                            "cutoff", cutoff.toString(),
                            "retentionDays", retentionDays,
                            "deletedEventCount", deleted
                    ));
        }
        return deleted;
    }
}
