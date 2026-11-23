package com.oneforall.padobackend.config;

import com.oneforall.padobackend.service.AuditRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.audit.retention.enabled", havingValue = "true")
public class AuditRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuditRetentionScheduler.class);

    private final AuditRetentionService retentionService;

    public AuditRetentionScheduler(AuditRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${app.audit.retention.cron:0 30 3 * * *}", zone = "UTC")
    public void purgeExpiredEvents() {
        int deleted = retentionService.purgeExpiredEvents();
        if (deleted > 0) log.info("Purged {} expired audit events", deleted);
    }
}
