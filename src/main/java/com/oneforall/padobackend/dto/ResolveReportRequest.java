package com.oneforall.padobackend.dto;

import com.oneforall.padobackend.entity.ReportResolutionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
        @NotBlank @Size(max = 2000) String resolutionNote,
        @NotNull ReportResolutionAction action
) {}
