package com.havenbank.backend.notification.domain;

/**
 * Classification that governs suppressibility (FR-7.1a/7.1b).
 * {@link #SECURITY_CRITICAL} notifications are mandatory and cannot be opted out of;
 * {@link #CONVENIENCE} notifications are user-configurable.
 */
public enum NotificationCategory {
    SECURITY_CRITICAL,
    CONVENIENCE
}
