package com.havenbank.backend.audit.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.shared.ratelimit.RateLimitExceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Records throttled requests on the audit trail asynchronously, off the request path.
 */
@Component
@RequiredArgsConstructor
class RateLimitAuditListener {

    private final AuditService auditService;

    @Async
    @EventListener
    void onRateLimitExceeded(RateLimitExceededEvent event) {
        auditService.record(
                AuditEvent.failure(null, AuditAction.RATE_LIMITED, event.tier() + " " + event.path()),
                new AuditContext(event.sourceIp(), event.userAgent(), event.correlationId()));
    }
}