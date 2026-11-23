package com.oneforall.padobackend.audit;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRequestFilterTests {
    private final AuditRequestContextHolder contextHolder = new AuditRequestContextHolder();
    private final AuditRequestFilter filter = new AuditRequestFilter(contextHolder);

    @Test
    void capturesRequestMetadataAndClearsThreadLocalAfterCompletion() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(AuditRequestFilter.REQUEST_ID_HEADER, "web-req_123");
        request.addHeader("User-Agent", "PADO Web");
        request.setRemoteAddr("203.0.113.20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<AuditRequestContext> captured = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                captured.set(contextHolder.current().orElseThrow()));

        assertThat(captured.get().requestId()).isEqualTo("web-req_123");
        assertThat(captured.get().ipAddress()).isEqualTo("203.0.113.20");
        assertThat(captured.get().userAgent()).isEqualTo("PADO Web");
        assertThat(captured.get().httpMethod()).isEqualTo("POST");
        assertThat(captured.get().requestPath()).isEqualTo("/api/orders");
        assertThat(response.getHeader(AuditRequestFilter.REQUEST_ID_HEADER)).isEqualTo("web-req_123");
        assertThat(contextHolder.current()).isEmpty();
    }

    @Test
    void replacesUnsafeRequestIds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(AuditRequestFilter.REQUEST_ID_HEADER, "invalid request id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> generated = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                generated.set(contextHolder.current().orElseThrow().requestId()));

        assertThat(generated.get()).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(AuditRequestFilter.REQUEST_ID_HEADER)).isEqualTo(generated.get());
    }
}
