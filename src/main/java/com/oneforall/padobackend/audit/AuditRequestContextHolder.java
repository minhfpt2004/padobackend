package com.oneforall.padobackend.audit;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditRequestContextHolder {
    private final ThreadLocal<AuditRequestContext> current = new ThreadLocal<>();

    public void set(AuditRequestContext context) {
        current.set(context);
    }

    public Optional<AuditRequestContext> current() {
        return Optional.ofNullable(current.get());
    }

    public void clear() {
        current.remove();
    }
}
