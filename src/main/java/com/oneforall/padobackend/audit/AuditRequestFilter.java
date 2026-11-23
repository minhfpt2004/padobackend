package com.oneforall.padobackend.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditRequestFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final int MAX_USER_AGENT_LENGTH = 1024;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final AuditRequestContextHolder contextHolder;

    public AuditRequestFilter(AuditRequestContextHolder contextHolder) {
        this.contextHolder = contextHolder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        contextHolder.set(new AuditRequestContext(
                requestId,
                clean(request.getRemoteAddr()),
                truncate(clean(request.getHeader("User-Agent")), MAX_USER_AGENT_LENGTH),
                request.getMethod(),
                request.getRequestURI(),
                Instant.now()
        ));
        try {
            filterChain.doFilter(request, response);
        } finally {
            contextHolder.clear();
        }
    }

    private String resolveRequestId(String candidate) {
        String cleaned = clean(candidate);
        return cleaned != null && SAFE_REQUEST_ID.matcher(cleaned).matches()
                ? cleaned
                : UUID.randomUUID().toString();
    }

    private String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
