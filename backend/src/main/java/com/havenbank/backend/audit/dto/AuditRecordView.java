package com.havenbank.backend.audit.dto;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.domain.AuditTrail;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection of an audit record for Staff/Admin.
 *
 * <p>{@code actor} is a human-readable label resolved by the controller: the acting user's email
 * for authenticated actions, or the attempted identity for events that have no principal (e.g. a
 * failed login). {@code actorUserId} is retained as the stable machine id.
 */
public record AuditRecordView(
        UUID id, UUID actorUserId, String actor, AuditAction action, String targetType, String targetId,
        AuditEvent.Outcome outcome, String detail, String sourceIp, String userAgent,
        String correlationId, Instant createdAt) {

    public static AuditRecordView of(AuditTrail a, String actor) {
        return new AuditRecordView(a.getId(), a.getActorUserId(), actor, a.getAction(), a.getTargetType(),
                a.getTargetId(), a.getOutcome(), a.getDetail(), a.getSourceIp(), a.getUserAgent(),
                a.getCorrelationId(), a.getCreatedAt());
    }
}