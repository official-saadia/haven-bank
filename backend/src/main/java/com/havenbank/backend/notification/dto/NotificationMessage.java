package com.havenbank.backend.notification.dto;

import com.havenbank.backend.notification.domain.NotificationCategory;
import com.havenbank.backend.notification.domain.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * A notification to be delivered. Carries the recipient email (for delivery) and optionally the
 * recipient user id (so convenience notifications can be gated by stored preferences). Never contains
 * secrets beyond what a channel legitimately needs (FR-7.5).
 */
public record NotificationMessage(
        String recipientEmail,
        String recipientName,
        UUID recipientUserId,
        NotificationType type,
        Map<String, String> parameters
) {
    /**
     * Convenience form for messages addressed only by email (no preference gating).
     */
    public NotificationMessage(String recipientEmail, String recipientName,
                               NotificationType type, Map<String, String> parameters) {
        this(recipientEmail, recipientName, null, type, parameters);
    }

    public NotificationCategory category() {
        return type.category();
    }
}
