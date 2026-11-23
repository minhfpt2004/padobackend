package com.oneforall.padobackend.service;

import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditRetentionServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Test
    void purgeDeletesOnlyExpiredEventsAndAuditsTheCleanup() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        AuditService auditService = mock(AuditService.class);
        Instant cutoff = Instant.parse("2026-10-17T12:00:00Z");
        when(events.deleteCreatedBefore(cutoff)).thenReturn(12);
        AuditRetentionService service = new AuditRetentionService(
                events, auditService, 30, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.purgeExpiredEvents()).isEqualTo(12);

        verify(events).deleteCreatedBefore(cutoff);
        verify(auditService).recordSystemSuccess(
                eq(AuditEventType.AUDIT_RETENTION_PURGED),
                eq(AuditTargetType.SYSTEM),
                eq("audit-events"),
                argThat((Map<String, Object> metadata) -> metadata.get("deletedEventCount").equals(12)
                        && metadata.get("retentionDays").equals(30)
                        && metadata.get("cutoff").equals(cutoff.toString()))
        );
    }

    @Test
    void purgeDoesNotCreateNoiseWhenNothingExpired() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        AuditService auditService = mock(AuditService.class);
        AuditRetentionService service = new AuditRetentionService(
                events, auditService, 30, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.purgeExpiredEvents()).isZero();

        verify(events).deleteCreatedBefore(Instant.parse("2026-10-17T12:00:00Z"));
        verifyNoInteractions(auditService);
    }

    @Test
    void retentionDaysMustBePositive() {
        assertThatThrownBy(() -> new AuditRetentionService(
                mock(AuditEventRepository.class), mock(AuditService.class), 0,
                Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
