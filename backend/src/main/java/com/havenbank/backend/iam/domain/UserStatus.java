package com.havenbank.backend.iam.domain;

/**
 * Lifecycle state of a user account. Deactivation is a status transition, never a hard delete.
 */
public enum UserStatus {
    /**
     * Registered but email not yet verified; cannot authenticate.
     */
    PENDING_VERIFICATION,
    /**
     * Verified and usable.
     */
    ACTIVE,
    /**
     * Temporarily locked (e.g. after repeated failed logins).
     */
    LOCKED,
    /**
     * Permanently closed.
     */
    CLOSED
}
