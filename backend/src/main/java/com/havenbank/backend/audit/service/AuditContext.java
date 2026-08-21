package com.havenbank.backend.audit.service;

import com.havenbank.backend.shared.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The ambient request context stamped onto an audit record: the source IP, user agent and
 * correlation id of the request that triggered the event.
 *
 * <p>These values live in thread-locals ({@link MDC} and {@link RequestContextHolder}) that are only
 * populated on the request thread. A caller running <em>off</em> that thread &mdash; e.g. an
 * {@code @Async} event listener &mdash; must capture the context on the request thread (via
 * {@link #current()}, or by copying the fields into the event it publishes) and pass it explicitly
 * to {@link AuditService#record(com.havenbank.backend.audit.domain.AuditEvent, AuditContext)}.
 * By the time an async task runs, those thread-locals are empty, so an audit written there would
 * otherwise lose its IP, user agent and correlation id.
 */
public record AuditContext(String sourceIp, String userAgent, String correlationId) {

    /**
     * Capture from the current request thread. Fields are null when no request is bound (e.g. a
     * scheduled job); the audit columns are nullable, so that is acceptable.
     */
    public static AuditContext current() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String ip = null;
        String userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            ip = attrs.getRequest().getRemoteAddr();
            userAgent = attrs.getRequest().getHeader("User-Agent");
        }
        return new AuditContext(ip, userAgent, correlationId);
    }
}
