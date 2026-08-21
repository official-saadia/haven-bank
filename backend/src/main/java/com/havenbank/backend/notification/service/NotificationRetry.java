package com.havenbank.backend.notification.service;

import java.time.Duration;
import java.time.Instant;

/** Retry policy for notification delivery: at most 3 attempts, exponential backoff, then dead-letter. */
final class NotificationRetry {

    static final int MAX_ATTEMPTS = 3;

    private NotificationRetry() {
    }

    /** When to schedule the given (1-based) attempt: exponential backoff, capped. */
    static Instant nextAttempt(int attemptNumber) {
        long minutes = 1L << Math.min(attemptNumber, 6); // 2, 4, 8, ... capped at 64 min
        return Instant.now().plus(Duration.ofMinutes(minutes));
    }

    /** A short, storable form of a failure cause for the last_error column. */
    static String errorText(Throwable t) {
        String msg = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
