package com.havenbank.backend.audit.service;

import com.havenbank.backend.audit.domain.AuditEvent;

/**
 * Published entry point for recording audit events. Other modules depend on this interface only;
 * they never see the underlying entity or repository.
 */
public interface AuditService {

    /**
     * Persist an audit event, enriching it with correlation id, source IP and user agent captured
     * from the current request thread.
     */
    void record(AuditEvent event);

    /**
     * Persist an audit event with an explicitly supplied {@link AuditContext}, for callers running
     * off the request thread (e.g. an {@code @Async} listener) where the thread-locals the no-arg
     * overload reads are empty. Capture the context on the request thread and pass it here.
     */
    void record(AuditEvent event, AuditContext context);
}