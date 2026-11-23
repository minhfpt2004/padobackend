package com.oneforall.padobackend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ReportPageResponse(
        List<ReportResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static ReportPageResponse from(Page<ReportResponse> result) {
        return new ReportPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
