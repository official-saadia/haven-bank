package com.havenbank.backend.shared.ratelimit;

/**
 * What a tier's limit is counted against.
 */
public enum KeyType {
    /**
     * Client IP. Used for unauthenticated endpoints, where there is no subject yet.
     */
    IP,
    /**
     * Authenticated subject from the bearer token, so one user cannot spend another's budget.
     */
    SUBJECT
}
