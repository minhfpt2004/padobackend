package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.Report;
import com.oneforall.padobackend.entity.ReportReason;
import com.oneforall.padobackend.entity.ReportResolutionAction;
import com.oneforall.padobackend.entity.ReportStatus;
import com.oneforall.padobackend.entity.ReportTargetType;

import java.time.Instant;
import java.util.List;

public record ReportResponse(
        Long id,
        long version,
        Long reporterId,
        String reporterEmail,
        ReportTargetType targetType,
        Long targetId,
        String targetLabel,
        Long reportedUserId,
        ReportReason reason,
        String details,
        List<String> evidenceUrls,
        ReportStatus status,
        Long assignedAdminId,
        String assignedAdminEmail,
        String internalNote,
        String resolutionNote,
        ReportResolutionAction resolutionAction,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {
    public static ReportResponse from(Report report, boolean includeInternalNote) {
        return new ReportResponse(
                report.getId(),
                report.getVersion(),
                report.getReporter().getId(),
                report.getReporter().getEmail(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetLabel(),
                report.getReportedUserId(),
                report.getReason(),
                report.getDetails(),
                List.copyOf(report.getEvidenceUrls()),
                report.getStatus(),
                report.getAssignedAdmin() == null ? null : report.getAssignedAdmin().getId(),
                report.getAssignedAdmin() == null ? null : report.getAssignedAdmin().getEmail(),
                includeInternalNote ? report.getInternalNote() : null,
                report.getResolutionNote(),
                report.getResolutionAction(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                report.getResolvedAt()
        );
    }
}
