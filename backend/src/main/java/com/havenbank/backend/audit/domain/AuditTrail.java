package com.havenbank.backend.audit.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record. There is intentionally no setter for existing rows and no update/delete
 * pathway anywhere in the application (FR-5.1).
 */
@Entity
@Table(name = "audit_trail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditTrail {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AuditAction action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 128)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditEvent.Outcome outcome;

    @Column(length = 512)
    private String detail;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuditTrail from(AuditEvent e, String sourceIp, String userAgent, String correlationId) {
        AuditTrail a = new AuditTrail();
        a.id = UUID.randomUUID();
        a.actorUserId = e.actorUserId();
        a.action = e.action();
        a.targetType = e.targetType();
        a.targetId = e.targetId();
        a.outcome = e.outcome();
        a.detail = e.detail();
        a.sourceIp = sourceIp;
        a.userAgent = userAgent;
        a.correlationId = correlationId;
        return a;
    }
}
