package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.ClaimReportRequest;
import com.oneforall.padobackend.dto.DismissReportRequest;
import com.oneforall.padobackend.dto.ReportPageResponse;
import com.oneforall.padobackend.dto.ReportResponse;
import com.oneforall.padobackend.dto.ResolveReportRequest;
import com.oneforall.padobackend.entity.ReportStatus;
import com.oneforall.padobackend.entity.ReportTargetType;
import com.oneforall.padobackend.service.AdminReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {
    private final AdminReportService reportService;

    public AdminReportController(AdminReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    ReportPageResponse search(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) Long reporterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportService.search(status, targetType, reporterId, page, size);
    }

    @GetMapping("/{id}")
    ReportResponse findOne(@PathVariable Long id) {
        return reportService.findOne(id);
    }

    @PatchMapping("/{id}/claim")
    ReportResponse claim(@PathVariable Long id,
                         @Valid @RequestBody ClaimReportRequest request,
                         Authentication authentication) {
        return reportService.claim(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/resolve")
    ReportResponse resolve(@PathVariable Long id,
                           @Valid @RequestBody ResolveReportRequest request,
                           Authentication authentication) {
        return reportService.resolve(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/dismiss")
    ReportResponse dismiss(@PathVariable Long id,
                           @Valid @RequestBody DismissReportRequest request,
                           Authentication authentication) {
        return reportService.dismiss(id, request, authentication.getName());
    }
}
