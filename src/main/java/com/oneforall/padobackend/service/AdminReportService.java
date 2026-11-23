package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.ClaimReportRequest;
import com.oneforall.padobackend.dto.DismissReportRequest;
import com.oneforall.padobackend.dto.ReportPageResponse;
import com.oneforall.padobackend.dto.ReportResponse;
import com.oneforall.padobackend.dto.ResolveReportRequest;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.Report;
import com.oneforall.padobackend.entity.ReportResolutionAction;
import com.oneforall.padobackend.entity.ReportStatus;
import com.oneforall.padobackend.entity.ReportTargetType;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.entity.UserModerationStatus;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.ReportRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminReportService {
    private final ReportRepository reports;
    private final UserRepository users;
    private final ProductRepository products;
    private final AuditService auditService;

    public AdminReportService(ReportRepository reports, UserRepository users,
                              ProductRepository products, AuditService auditService) {
        this.reports = reports;
        this.users = users;
        this.products = products;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ReportPageResponse search(ReportStatus status, ReportTargetType targetType,
                                     Long reporterId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = reports.search(status, targetType, reporterId,
                        PageRequest.of(Math.max(page, 0), safeSize,
                                Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(report -> ReportResponse.from(report, true));
        return ReportPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ReportResponse findOne(Long id) {
        return ReportResponse.from(requireReport(id), true);
    }

    @Transactional
    public ReportResponse claim(Long id, ClaimReportRequest request, String adminEmail) {
        User admin = requireUser(adminEmail);
        Report report = requireReport(id);
        if (report.getStatus() == ReportStatus.IN_REVIEW
                && report.getAssignedAdmin() != null
                && report.getAssignedAdmin().getId().equals(admin.getId())) {
            if (request.internalNote() != null) report.setInternalNote(clean(request.internalNote()));
            return ReportResponse.from(report, true);
        }
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ConflictException("Only open reports can be claimed");
        }
        report.setStatus(ReportStatus.IN_REVIEW);
        report.setAssignedAdmin(admin);
        report.setInternalNote(clean(request.internalNote()));
        Report saved = reports.save(report);
        auditService.recordSuccess(AuditEventType.REPORT_CLAIMED, admin,
                AuditTargetType.REPORT, saved.getId(), auditMetadata(saved));
        return ReportResponse.from(saved, true);
    }

    @Transactional
    public ReportResponse resolve(Long id, ResolveReportRequest request, String adminEmail) {
        User admin = requireUser(adminEmail);
        Report report = requireAssignedReport(id, admin);
        applyAction(report, request.action(), request.resolutionNote(), admin);
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolutionNote(request.resolutionNote().trim());
        report.setResolutionAction(request.action());
        report.setResolvedAt(Instant.now());
        Report saved = reports.save(report);
        auditService.recordSuccess(AuditEventType.REPORT_RESOLVED, admin,
                AuditTargetType.REPORT, saved.getId(), auditMetadata(saved));
        return ReportResponse.from(saved, true);
    }

    @Transactional
    public ReportResponse dismiss(Long id, DismissReportRequest request, String adminEmail) {
        User admin = requireUser(adminEmail);
        Report report = requireAssignedReport(id, admin);
        report.setStatus(ReportStatus.DISMISSED);
        report.setResolutionNote(request.resolutionNote().trim());
        report.setResolutionAction(ReportResolutionAction.NONE);
        report.setResolvedAt(Instant.now());
        Report saved = reports.save(report);
        auditService.recordSuccess(AuditEventType.REPORT_DISMISSED, admin,
                AuditTargetType.REPORT, saved.getId(), auditMetadata(saved));
        return ReportResponse.from(saved, true);
    }

    private void applyAction(Report report, ReportResolutionAction action, String note, User admin) {
        switch (action) {
            case NONE -> { }
            case HIDE_PRODUCT -> hideProduct(report, note, admin);
            case WARN_USER -> moderateUser(report, note, admin, UserModerationStatus.WARNED,
                    AuditEventType.USER_WARNED);
            case SUSPEND_USER -> moderateUser(report, note, admin, UserModerationStatus.SUSPENDED,
                    AuditEventType.USER_SUSPENDED);
        }
    }

    private void hideProduct(Report report, String note, User admin) {
        if (report.getTargetType() != ReportTargetType.PRODUCT) {
            throw new ConflictException("HIDE_PRODUCT is only valid for product reports");
        }
        Product product = products.findById(report.getTargetId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        product.setModerationHidden(true);
        product.setModerationReason(note.trim());
        products.save(product);
        auditService.recordSuccess(AuditEventType.PRODUCT_MODERATION_HIDDEN, admin,
                AuditTargetType.PRODUCT, product.getId(), Map.of("reportId", report.getId()));
    }

    private void moderateUser(Report report, String note, User admin, UserModerationStatus status,
                              AuditEventType eventType) {
        if (report.getReportedUserId() == null) {
            throw new ConflictException("This report does not identify a reported user");
        }
        User target = users.findById(report.getReportedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Reported user not found"));
        if (target.getRole() == Role.ADMIN) {
            throw new ConflictException("Administrators cannot be moderated from a report");
        }
        target.setModerationStatus(status);
        target.setModerationReason(note.trim());
        users.save(target);
        auditService.recordSuccess(eventType, admin, AuditTargetType.USER, target.getId(),
                Map.of("reportId", report.getId(), "status", status.name()));
    }

    private Report requireAssignedReport(Long id, User admin) {
        Report report = requireReport(id);
        if (report.getStatus() != ReportStatus.IN_REVIEW) {
            throw new ConflictException("The report must be in review before it can be completed");
        }
        if (report.getAssignedAdmin() == null
                || !report.getAssignedAdmin().getId().equals(admin.getId())) {
            throw new ConflictException("The report is assigned to another administrator");
        }
        return report;
    }

    private Report requireReport(Long id) {
        return reports.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Administrator not found"));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> auditMetadata(Report report) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetType", report.getTargetType().name());
        metadata.put("targetId", report.getTargetId());
        metadata.put("status", report.getStatus().name());
        if (report.getResolutionAction() != null) {
            metadata.put("resolutionAction", report.getResolutionAction().name());
        }
        if (report.getReportedUserId() != null) metadata.put("reportedUserId", report.getReportedUserId());
        return metadata;
    }
}
