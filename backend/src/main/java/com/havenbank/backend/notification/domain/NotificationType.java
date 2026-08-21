package com.havenbank.backend.notification.domain;

/**
 * The catalogue of notifications the platform can emit, each bound to a fixed category.
 */
public enum NotificationType {

    ACCOUNT_CREATED(NotificationCategory.CONVENIENCE),
    EMAIL_VERIFICATION(NotificationCategory.SECURITY_CRITICAL),
    /**
     * Someone tried to register with an address that already has an account.
     */
    REGISTRATION_ATTEMPT_EXISTING(NotificationCategory.SECURITY_CRITICAL),
    PASSWORD_CHANGED(NotificationCategory.SECURITY_CRITICAL),
    PASSWORD_RESET_REQUESTED(NotificationCategory.SECURITY_CRITICAL),
    LOGIN_OTP(NotificationCategory.SECURITY_CRITICAL),
    STEP_UP_OTP(NotificationCategory.SECURITY_CRITICAL),
    MONEY_MOVEMENT(NotificationCategory.CONVENIENCE),
    /**
     * Admin-initiated permanent account closure.
     */
    ACCOUNT_CLOSED(NotificationCategory.SECURITY_CRITICAL);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }

    /**
     * Whether this notification may be persisted to the retry outbox and re-sent. False for types
     * whose body carries a secret (OTP codes, verification/reset links): those must never be stored
     * (FR-5.3, FR-1.8b) and are time-sensitive, so they are immediate best-effort only.
     */
    public boolean retryable() {
        return switch (this) {
            case EMAIL_VERIFICATION, PASSWORD_RESET_REQUESTED, LOGIN_OTP, STEP_UP_OTP -> false;
            default -> true;
        };
    }
}