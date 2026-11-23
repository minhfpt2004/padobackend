package com.oneforall.padobackend.service;

import com.oneforall.padobackend.audit.AuditRequestContext;
import com.oneforall.padobackend.audit.AuditRequestContextHolder;
import com.oneforall.padobackend.dto.AuditEventCommand;
import com.oneforall.padobackend.dto.AuditEventResponse;
import com.oneforall.padobackend.entity.AuditEvent;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AuditService {
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password", "token", "otp", "secret", "authorization", "cookie", "credential"
    );

    private final AuditEventRepository events;
    private final AuditRequestContextHolder requestContextHolder;

    public AuditService(AuditEventRepository events, AuditRequestContextHolder requestContextHolder) {
        this.events = events;
        this.requestContextHolder = requestContextHolder;
    }

    @Transactional
    public AuditEventResponse record(AuditEventCommand command) {
        return persist(command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEventResponse recordIsolated(AuditEventCommand command) {
        return persist(command);
    }

    @Transactional
    public AuditEventResponse recordSuccess(AuditEventType eventType, User actor,
                                            AuditTargetType targetType, Object targetId,
                                            Map<String, Object> metadata) {
        Objects.requireNonNull(actor, "Audit actor is required");
        return persist(new AuditEventCommand(
                eventType,
                AuditOutcome.SUCCESS,
                actor.getRole() == Role.ADMIN ? AuditSource.ADMIN : AuditSource.USER,
                actor.getId(),
                actor.getEmail(),
                actor.getRole(),
                targetType,
                targetId == null ? null : String.valueOf(targetId),
                null,
                null,
                null,
                metadata,
                null
        ));
    }

    @Transactional
    public AuditEventResponse recordSystemSuccess(AuditEventType eventType,
                                                  AuditTargetType targetType, Object targetId,
                                                  Map<String, Object> metadata) {
        return persist(new AuditEventCommand(
                eventType,
                AuditOutcome.SUCCESS,
                AuditSource.SYSTEM,
                null,
                null,
                null,
                targetType,
                targetId == null ? null : String.valueOf(targetId),
                null,
                null,
                null,
                metadata,
                null
        ));
    }

    private AuditEventResponse persist(AuditEventCommand command) {
        Objects.requireNonNull(command, "Audit command is required");
        Objects.requireNonNull(command.eventType(), "Audit event type is required");
        AuditOutcome outcome = Objects.requireNonNull(command.outcome(), "Audit outcome is required");
        AuditSource source = Objects.requireNonNull(command.source(), "Audit source is required");
        AuditRequestContext requestContext = requestContextHolder.current().orElse(null);

        AuditEvent event = new AuditEvent(
                command.eventType(), outcome, source,
                command.actorUserId(), subjectUserId(command), clean(command.actorEmail()), command.actorRole(),
                command.targetType(), clean(command.targetId()),
                firstPresent(command.requestId(), requestContext == null ? null : requestContext.requestId()),
                firstPresent(command.ipAddress(), requestContext == null ? null : requestContext.ipAddress()),
                firstPresent(command.userAgent(), requestContext == null ? null : requestContext.userAgent()),
                requestMetadata(command.metadata(), requestContext), clean(command.errorCode())
        );
        return AuditEventResponse.from(events.save(event));
    }

    private Long subjectUserId(AuditEventCommand command) {
        Long metadataUserId = metadataUserId(command.metadata(), "beneficiaryUserId", "buyerUserId", "ownerUserId");
        if (metadataUserId != null) return metadataUserId;
        if (command.targetType() == AuditTargetType.USER) {
            try {
                return command.targetId() == null ? command.actorUserId() : Long.valueOf(command.targetId());
            } catch (NumberFormatException ignored) {
                return command.actorUserId();
            }
        }
        return command.actorUserId();
    }

    private Long metadataUserId(Map<String, Object> metadata, String... keys) {
        if (metadata == null) return null;
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Number number) return number.longValue();
            if (value instanceof String text) {
                try {
                    return Long.valueOf(text);
                } catch (NumberFormatException ignored) {
                    // Try the next supported metadata key.
                }
            }
        }
        return null;
    }

    private Map<String, Object> requestMetadata(Map<String, Object> metadata, AuditRequestContext requestContext) {
        Map<String, Object> enriched = new LinkedHashMap<>(redact(metadata));
        if (requestContext != null) {
            enriched.putIfAbsent("httpMethod", requestContext.httpMethod());
            enriched.putIfAbsent("requestPath", requestContext.requestPath());
            enriched.putIfAbsent("requestStartedAt", requestContext.startedAt().toString());
        }
        return enriched;
    }

    private Map<String, Object> redact(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null) return;
            safe.put(key, isSensitive(key) ? "[REDACTED]" : redactValue(value));
        });
        return safe;
    }

    private Object redactValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> safe = new LinkedHashMap<>();
            nestedMap.forEach((key, nestedValue) -> {
                if (key == null) return;
                String name = String.valueOf(key);
                safe.put(name, isSensitive(name) ? "[REDACTED]" : redactValue(nestedValue));
            });
            return safe;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> safe = new java.util.ArrayList<>();
            values.forEach(item -> safe.add(redactValue(item)));
            return safe;
        }
        return value;
    }

    private boolean isSensitive(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstPresent(String preferred, String fallback) {
        String cleaned = clean(preferred);
        return cleaned == null ? clean(fallback) : cleaned;
    }
}
