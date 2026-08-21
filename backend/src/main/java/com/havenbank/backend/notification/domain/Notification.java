package com.havenbank.backend.notification.domain;

import com.havenbank.backend.notification.dto.NotificationMessage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A record of a notification the system produced. Terminal states (SENT, SUPPRESSED, FAILED) are a
 * simple audit row; retryable notifications additionally carry outbox state (attempts, next attempt,
 * stored subject/body) so the retry worker can resend them until they succeed or dead-letter.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NotificationCategory category;

    @Column(nullable = false, length = 16)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    // Stored only for retryable (non-secret) notifications, so the worker can resend without
    // re-rendering. Cleared once delivered. Never populated for secret-bearing types.
    @Column(length = 200)
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Terminal/simple record (SENT, SUPPRESSED, FAILED) — no retry state, no stored content. */
    public static Notification of(NotificationMessage m, String channel, Status status) {
        Notification n = base(m, channel);
        n.status = status;
        return n;
    }

    /** A retryable notification enqueued for (re)delivery, carrying the content to resend. */
    public static Notification pending(NotificationMessage m, String channel, String subject, String body) {
        Notification n = base(m, channel);
        n.status = Status.PENDING;
        n.subject = subject;
        n.body = body;
        n.nextAttemptAt = Instant.now();
        return n;
    }

    private static Notification base(NotificationMessage m, String channel) {
        Notification n = new Notification();
        n.id = UUID.randomUUID();
        n.userId = m.recipientUserId();
        n.recipientEmail = m.recipientEmail();
        n.type = m.type();
        n.category = m.category();
        n.channel = channel;
        return n;
    }

    /** Delivered: mark SENT and drop the stored content. */
    public void markSent() {
        this.status = Status.SENT;
        this.subject = null;
        this.body = null;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * A delivery attempt failed. Increments the attempt count; reschedules for {@code nextAttempt}
     * if retries remain, otherwise moves to DEAD_LETTER.
     */
    public void recordFailure(String error, int maxAttempts, Instant nextAttempt) {
        this.attempts += 1;
        this.lastError = error;
        if (this.attempts >= maxAttempts) {
            this.status = Status.DEAD_LETTER;
            this.nextAttemptAt = null;
        } else {
            this.status = Status.PENDING;
            this.nextAttemptAt = nextAttempt;
        }
    }

    /** Admin action: return a dead-lettered notification to the queue for a fresh set of attempts. */
    public void requeue() {
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = Instant.now();
        this.lastError = null;
    }
}
