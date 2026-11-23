package com.oneforall.padobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DismissReportRequest(@NotBlank @Size(max = 2000) String resolutionNote) {}
