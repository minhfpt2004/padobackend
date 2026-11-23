package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.AuditCsvExport;
import com.oneforall.padobackend.dto.AuditEventPageResponse;
import com.oneforall.padobackend.dto.AuditEventResponse;
import com.oneforall.padobackend.dto.AuditEventSearchCriteria;
import com.oneforall.padobackend.entity.AuditEvent;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.AuditEventRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAuditService {
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final AuditEventRepository events;
    private final UserRepository users;
    private final AuditService auditService;
    private final AuditCsvExporter csvExporter;
    private final int exportMaxRows;

    public AdminAuditService(AuditEventRepository events, UserRepository users,
                             AuditService auditService, AuditCsvExporter csvExporter,
                             @Value("${app.audit.export.max-rows:10000}") int exportMaxRows) {
        if (exportMaxRows < 1 || exportMaxRows > 100_000) {
            throw new IllegalArgumentException("Audit export max rows must be between 1 and 100000");
        }
        this.events = events;
        this.users = users;
        this.auditService = auditService;
        this.csvExporter = csvExporter;
        this.exportMaxRows = exportMaxRows;
    }

    @Transactional(readOnly = true)
    public AuditEventPageResponse search(AuditEventSearchCriteria criteria, int page, int size) {
        validateRange(criteria.from(), criteria.to());
        return find(baseSpecification(criteria), page, size);
    }

    @Transactional(readOnly = true)
    public AuditEventPageResponse customerTimeline(Long userId, AuditEventSearchCriteria criteria,
                                                   int page, int size) {
        if (userId == null || userId <= 0) throw new BadRequestException("User id must be positive");
        validateRange(criteria.from(), criteria.to());
        Specification<AuditEvent> relatedToUser = (root, query, cb) -> cb.or(
                cb.equal(root.get("actorUserId"), userId),
                cb.equal(root.get("subjectUserId"), userId),
                cb.and(
                        cb.equal(root.get("targetType"), AuditTargetType.USER),
                        cb.equal(root.get("targetId"), String.valueOf(userId))
                )
        );
        return find(baseSpecification(criteria).and(relatedToUser), page, size);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse findById(Long id) {
        return events.findById(id)
                .map(AuditEventResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event not found"));
    }

    @Transactional
    public AuditCsvExport export(AuditEventSearchCriteria criteria, String adminEmail) {
        validateRange(criteria.from(), criteria.to());
        User actor = users.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        List<AuditEvent> matches = events.findAll(
                baseSpecification(criteria),
                PageRequest.of(0, exportMaxRows + 1, NEWEST_FIRST)
        ).getContent();
        boolean truncated = matches.size() > exportMaxRows;
        List<AuditEventResponse> exported = matches.stream()
                .limit(exportMaxRows)
                .map(AuditEventResponse::from)
                .toList();
        byte[] content = csvExporter.write(exported);

        Map<String, Object> metadata = filterMetadata(criteria);
        metadata.put("rowCount", exported.size());
        metadata.put("maxRows", exportMaxRows);
        metadata.put("truncated", truncated);
        auditService.recordSuccess(AuditEventType.AUDIT_EXPORTED, actor,
                AuditTargetType.SYSTEM, "audit-events", metadata);

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        return new AuditCsvExport(content, exported.size(), truncated,
                "pado-audit-" + timestamp + "Z.csv");
    }

    private AuditEventPageResponse find(Specification<AuditEvent> specification, int page, int size) {
        Page<AuditEventResponse> result = events.findAll(
                        specification,
                        PageRequest.of(page, size, NEWEST_FIRST)
                )
                .map(AuditEventResponse::from);
        return AuditEventPageResponse.from(result);
    }

    private Specification<AuditEvent> baseSpecification(AuditEventSearchCriteria criteria) {
        Specification<AuditEvent> specification = (root, query, cb) -> cb.conjunction();
        if (criteria.actorUserId() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("actorUserId"), criteria.actorUserId()));
        }
        if (criteria.subjectUserId() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("subjectUserId"), criteria.subjectUserId()));
        }
        String actorEmail = clean(criteria.actorEmail());
        if (actorEmail != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(cb.lower(root.<String>get("actorEmail")), actorEmail.toLowerCase(java.util.Locale.ROOT)));
        }
        if (criteria.category() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("category"), criteria.category()));
        }
        if (criteria.eventType() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("eventType"), criteria.eventType()));
        }
        if (criteria.outcome() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("outcome"), criteria.outcome()));
        }
        if (criteria.source() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("source"), criteria.source()));
        }
        if (criteria.targetType() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("targetType"), criteria.targetType()));
        }
        String targetId = clean(criteria.targetId());
        if (targetId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("targetId"), targetId));
        }
        String requestId = clean(criteria.requestId());
        if (requestId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("requestId"), requestId));
        }
        if (criteria.from() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), criteria.from()));
        }
        if (criteria.to() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), criteria.to()));
        }
        return specification;
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Audit start time must not be after end time");
        }
    }

    private Map<String, Object> filterMetadata(AuditEventSearchCriteria criteria) {
        Map<String, Object> filters = new LinkedHashMap<>();
        put(filters, "actorUserId", criteria.actorUserId());
        put(filters, "subjectUserId", criteria.subjectUserId());
        put(filters, "actorEmail", clean(criteria.actorEmail()));
        put(filters, "category", criteria.category());
        put(filters, "eventType", criteria.eventType());
        put(filters, "outcome", criteria.outcome());
        put(filters, "source", criteria.source());
        put(filters, "targetType", criteria.targetType());
        put(filters, "targetId", clean(criteria.targetId()));
        put(filters, "requestId", clean(criteria.requestId()));
        put(filters, "from", criteria.from());
        put(filters, "to", criteria.to());
        return filters;
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value != null) values.put(key, value);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
