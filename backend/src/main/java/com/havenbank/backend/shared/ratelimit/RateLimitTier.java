package com.havenbank.backend.shared.ratelimit;

/**
 * Sensitivity bands. Thresholds are not defined here — they are bound from
 * {@code app.ratelimit.tiers} so limits can be tightened during an incident without a redeploy
 * (FR-6.6). This enum is only the identity of a band.
 */
public enum RateLimitTier {
    /**
     * Credential submission: sign-in, one-time code, registration, password reset.
     */
    CRITICAL,
    /**
     * Authenticated writes: money movement, profile changes, token exchange.
     */
    SENSITIVE,
    /**
     * Reads, and the interactive pages that render the credential forms.
     */
    STANDARD
}
