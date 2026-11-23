package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.CreateReportRequest;
import com.oneforall.padobackend.dto.ReportResponse;
import com.oneforall.padobackend.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReportResponse create(@Valid @RequestBody CreateReportRequest request, Authentication authentication) {
        return reportService.create(request, authentication.getName());
    }

    @GetMapping("/mine")
    List<ReportResponse> mine(Authentication authentication) {
        return reportService.mine(authentication.getName());
    }
}
