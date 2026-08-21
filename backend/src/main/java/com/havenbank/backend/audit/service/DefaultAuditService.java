package com.havenbank.backend.audit.service;

import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.domain.AuditTrail;
import com.havenbank.backend.audit.repository.AuditTrailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AuditService}. Enriches each event with ambient request context and writes it in a
 * {@link Propagation#REQUIRES_NEW new transaction}, so the audit record survives even if the calling
 * business transaction later rolls back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class DefaultAuditService implements AuditService {

    private final AuditTrailRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        // Sync callers run on the request thread, so capture the ambient context here.
        persist(event, AuditContext.current());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event, AuditContext context) {
        // Off-thread callers (e.g. @Async listeners) supply the context captured on the request thread.
        persist(event, context);
    }

    private void persist(AuditEvent event, AuditContext ctx) {
        repository.save(AuditTrail.from(event, ctx.sourceIp(), ctx.userAgent(), ctx.correlationId()));
        log.debug("Audit recorded: action={} outcome={}", event.action(), event.outcome());
    }
}