package com.havenbank.backend.audit.domain;

import java.util.UUID;

/**
 * Immutable description of a single auditable event, supplied by the calling module. Ambient
 * context (correlation id, source IP, user agent) is captured by the audit module itself, so callers
 * need only describe <em>what</em> happened.
 *
 * @param actorUserId the acting user, or {@code null} for anonymous/system actions
 * @param action      the action performed
 * @param targetType  the type of the affected entity (e.g. {@code "User"}); may be {@code null}
 * @param targetId    the id of the affected entity; may be {@code null}
 * @param outcome     whether the action succeeded or failed
 * @param detail      short, non-sensitive free text; never contains secrets (FR-5.3)
 */
public record AuditEvent(
        UUID actorUserId,
        AuditAction action,
        String targetType,
        String targetId,
        Outcome outcome,
        String detail
) {
    public enum Outcome {SUCCESS, FAILURE}

    /**
     * Convenience factory for a successful, target-less action.
     */
    public static AuditEvent success(UUID actorUserId, AuditAction action, String detail) {
        return new AuditEvent(actorUserId, action, null, null, Outcome.SUCCESS, detail);
    }

    /**
     * Convenience factory for a failed action.
     */
    public static AuditEvent failure(UUID actorUserId, AuditAction action, String detail) {
        return new AuditEvent(actorUserId, action, null, null, Outcome.FAILURE, detail);
    }
}
