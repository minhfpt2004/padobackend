package com.oneforall.padobackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneforall.padobackend.dto.AuditEventResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class AuditCsvExporter {
    private static final Pattern FORMULA_PREFIX = Pattern.compile("^[\\s]*[=+@-].*", Pattern.DOTALL);
    private static final List<String> HEADERS = List.of(
            "id", "createdAt", "category", "eventType", "outcome", "source",
            "actorUserId", "subjectUserId", "actorEmail", "actorRole",
            "targetType", "targetId", "requestId", "ipAddress", "userAgent",
            "errorCode", "metadata"
    );

    private final ObjectMapper objectMapper;

    public AuditCsvExporter() {
        this(new ObjectMapper());
    }

    AuditCsvExporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] write(List<AuditEventResponse> events) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendRow(csv, HEADERS.toArray());
        for (AuditEventResponse event : events) {
            appendRow(csv,
                    event.id(), event.createdAt(), event.category(), event.eventType(), event.outcome(), event.source(),
                    event.actorUserId(), event.subjectUserId(), event.actorEmail(), event.actorRole(),
                    event.targetType(), event.targetId(), event.requestId(), event.ipAddress(), event.userAgent(),
                    event.errorCode(), metadata(event));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String metadata(AuditEventResponse event) {
        try {
            return objectMapper.writeValueAsString(event.metadata());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit metadata for CSV export", exception);
        }
    }

    private void appendRow(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(field(values[index]));
        }
        csv.append("\r\n");
    }

    String field(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (FORMULA_PREFIX.matcher(text).matches()) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
