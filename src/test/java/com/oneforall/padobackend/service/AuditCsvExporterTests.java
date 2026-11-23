package com.oneforall.padobackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneforall.padobackend.dto.AuditEventResponse;
import com.oneforall.padobackend.entity.AuditCategory;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditOutcome;
import com.oneforall.padobackend.entity.AuditSource;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.Role;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCsvExporterTests {
    private final AuditCsvExporter exporter = new AuditCsvExporter(new ObjectMapper());

    @Test
    void exportUsesUtf8QuotesJsonAndNeutralizesSpreadsheetFormulas() {
        AuditEventResponse event = new AuditEventResponse(
                7L, AuditCategory.PRODUCT, AuditEventType.PRODUCT_CREATED,
                AuditOutcome.SUCCESS, AuditSource.USER, 10L, 10L,
                "=HYPERLINK(\"https://evil.test\")", Role.USER,
                AuditTargetType.PRODUCT, "42", "request-7", "127.0.0.1",
                "Browser, Version 1", Map.of("title", "+SUM(1,1)"),
                null, Instant.parse("2026-11-16T10:00:00Z")
        );

        String csv = new String(exporter.write(List.of(event)), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFF\"id\",\"createdAt\"");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"https://evil.test\"\")\"");
        assertThat(csv).contains("\"Browser, Version 1\"");
        assertThat(csv).contains("\"{\"\"title\"\":\"\"+SUM(1,1)\"\"}\"");
        assertThat(csv).endsWith("\r\n");
    }
}
