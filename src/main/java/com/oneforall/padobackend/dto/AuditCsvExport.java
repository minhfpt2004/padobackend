package com.oneforall.padobackend.dto;

public record AuditCsvExport(
        byte[] content,
        int rowCount,
        boolean truncated,
        String filename
) {
    public AuditCsvExport {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
