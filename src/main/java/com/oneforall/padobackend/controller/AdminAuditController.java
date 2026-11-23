package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.AuditCsvExport;
import com.oneforall.padobackend.dto.AuditEventPageResponse;
import com.oneforall.padobackend.dto.AuditEventResponse;
import com.oneforall.padobackend.dto.AuditEventSearchCriteria;
import com.oneforall.padobackend.dto.AuditFilterOptionsResponse;
import com.oneforall.padobackend.entity.AuditCategory;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.service.AdminAuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminAuditController {
    private final AdminAuditService auditService;

    public AdminAuditController(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit-events")
    public AuditEventPageResponse search(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long actorUserId,
            @RequestParam(required = false) @Positive Long subjectUserId,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditSource source,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return auditService.search(criteria(actorUserId, subjectUserId, actorEmail, category, eventType, outcome,
                source, targetType, targetId, requestId, from, to), page, size);
    }

    @GetMapping("/users/{userId}/audit-events")
    public AuditEventPageResponse customerTimeline(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditSource source,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return auditService.customerTimeline(userId,
                criteria(null, null, null, category, eventType, outcome, source,
                        targetType, targetId, requestId, from, to), page, size);
    }

    @GetMapping("/audit-events/filter-options")
    public AuditFilterOptionsResponse filterOptions() {
        return AuditFilterOptionsResponse.all();
    }

    @GetMapping("/audit-events/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @Positive Long actorUserId,
            @RequestParam(required = false) @Positive Long subjectUserId,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditSource source,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Authentication authentication
    ) {
        AuditCsvExport export = auditService.export(
                criteria(actorUserId, subjectUserId, actorEmail, category, eventType, outcome,
                        source, targetType, targetId, requestId, from, to),
                authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
                .header("X-Audit-Export-Rows", String.valueOf(export.rowCount()))
                .header("X-Audit-Export-Truncated", String.valueOf(export.truncated()))
                .body(export.content());
    }

    @GetMapping("/audit-events/{id}")
    public AuditEventResponse findById(@PathVariable @Positive Long id) {
        return auditService.findById(id);
    }

    private AuditEventSearchCriteria criteria(Long actorUserId, Long subjectUserId, String actorEmail,
                                              AuditCategory category, AuditEventType eventType,
                                              AuditOutcome outcome, AuditSource source,
                                              AuditTargetType targetType, String targetId,
                                              String requestId, Instant from, Instant to) {
        return new AuditEventSearchCriteria(actorUserId, subjectUserId, actorEmail, category, eventType,
                outcome, source, targetType, targetId, requestId, from, to);
    }
}
