package com.oneforall.padobackend;

import com.oneforall.padobackend.audit.AuditRequestContext;
import com.oneforall.padobackend.audit.AuditRequestContextHolder;
import com.oneforall.padobackend.dto.AuditEventCommand;
import com.oneforall.padobackend.dto.AuditEventResponse;
import com.oneforall.padobackend.entity.AuditCategory;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.repository.AuditEventRepository;
import com.oneforall.padobackend.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditServiceIntegrationTests {
    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private AuditRequestContextHolder requestContextHolder;

    @BeforeEach
    void clearAuditEvents() {
        requestContextHolder.clear();
        auditEvents.deleteAll();
    }

    @Test
    void auditEventsArePersistedWithDerivedCategoryAndSensitiveMetadataRedacted() {
        AuditEventResponse saved = auditService.record(new AuditEventCommand(
                AuditEventType.ORDER_CONFIRMED,
                AuditOutcome.SUCCESS,
                AuditSource.ADMIN,
                10L,
                " admin@pado.test ",
                Role.ADMIN,
                AuditTargetType.ORDER,
                "42",
                "req-123",
                "127.0.0.1",
                "JUnit",
                Map.of("previousStatus", "PENDING", "accessToken", "must-not-be-saved",
                        "request", Map.of("password", "also-must-not-be-saved")),
                null
        ));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.category()).isEqualTo(AuditCategory.ORDER);
        assertThat(saved.actorEmail()).isEqualTo("admin@pado.test");
        assertThat(saved.metadata())
                .containsEntry("previousStatus", "PENDING")
                .containsEntry("accessToken", "[REDACTED]");
        Map<?, ?> nestedRequest = (Map<?, ?>) saved.metadata().get("request");
        assertThat(nestedRequest.get("password")).isEqualTo("[REDACTED]");

        var persisted = auditEvents.findById(saved.id()).orElseThrow();
        assertThat(persisted.getEventType()).isEqualTo(AuditEventType.ORDER_CONFIRMED);
        assertThat(persisted.getMetadata()).containsEntry("accessToken", "[REDACTED]");
    }

    @Test
    void requestContextFillsMissingTransportMetadata() {
        requestContextHolder.set(new AuditRequestContext(
                "req-from-context", "203.0.113.10", "Browser Agent", "POST", "/api/orders", Instant.now()));
        try {
            AuditEventResponse saved = auditService.record(new AuditEventCommand(
                    AuditEventType.ORDER_CREATED,
                    AuditOutcome.SUCCESS,
                    AuditSource.USER,
                    20L,
                    "buyer@pado.test",
                    Role.USER,
                    AuditTargetType.ORDER,
                    "100",
                    null,
                    null,
                    null,
                    Map.of("productId", 50L),
                    null
            ));

            assertThat(saved.requestId()).isEqualTo("req-from-context");
            assertThat(saved.ipAddress()).isEqualTo("203.0.113.10");
            assertThat(saved.userAgent()).isEqualTo("Browser Agent");
            assertThat(saved.metadata())
                    .containsEntry("httpMethod", "POST")
                    .containsEntry("requestPath", "/api/orders");
        } finally {
            requestContextHolder.clear();
        }
    }
}
