package com.oneforall.padobackend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AuditEventPageResponse(
        List<AuditEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AuditEventPageResponse from(Page<AuditEventResponse> result) {
        return new AuditEventPageResponse(
                List.copyOf(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
