package com.oneforall.padobackend.dto;

import jakarta.validation.constraints.Size;

public record ClaimReportRequest(@Size(max = 2000) String internalNote) {}
