package com.havenbank.backend.money.domain;

/**
 * Lifecycle state. Deactivation is a status transition; accounts are never hard-deleted.
 */
public enum AccountStatus {ACTIVE, FROZEN, CLOSED}
