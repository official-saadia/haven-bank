package com.havenbank.backend.notification.dto;

import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.domain.Status;

import java.time.Instant;
import java.util.UUID;

/** Admin view of a notification record, used for dead-letter inspection. */
public record NotificationView(UUID id, UUID userId, String recipientEmail, NotificationType type,
                               Status status, int attempts, String lastError, Instant createdAt) {
}
