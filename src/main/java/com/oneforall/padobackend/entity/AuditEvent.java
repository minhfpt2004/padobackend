package com.oneforall.padobackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_events_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_events_actor", columnList = "actor_user_id, created_at"),
        @Index(name = "idx_audit_events_subject", columnList = "subject_user_id, created_at"),
        @Index(name = "idx_audit_events_category", columnList = "category, created_at"),
        @Index(name = "idx_audit_events_type", columnList = "event_type, created_at"),
        @Index(name = "idx_audit_events_target", columnList = "target_type, target_id"),
        @Index(name = "idx_audit_events_outcome", columnList = "outcome, created_at"),
        @Index(name = "idx_audit_events_request", columnList = "request_id")
})
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditSource source;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "subject_user_id")
    private Long subjectUserId;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 40)
    private AuditTargetType targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditEvent(AuditEventType eventType, AuditOutcome outcome, AuditSource source,
                      Long actorUserId, Long subjectUserId, String actorEmail, Role actorRole,
                      AuditTargetType targetType, String targetId,
                      String requestId, String ipAddress, String userAgent,
                      Map<String, Object> metadata, String errorCode) {
        this.category = eventType.getCategory();
        this.eventType = eventType;
        this.outcome = outcome;
        this.source = source;
        this.actorUserId = actorUserId;
        this.subjectUserId = subjectUserId;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.metadata = new LinkedHashMap<>(metadata);
        this.errorCode = errorCode;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
}
