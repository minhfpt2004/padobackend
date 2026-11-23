package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.ReportReason;
import com.oneforall.padobackend.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull @Positive Long targetId,
        @NotNull ReportReason reason,
        @Size(max = 2000) String details,
        @Size(max = 5) List<@Size(max = 2048) String> evidenceUrls
) {}
