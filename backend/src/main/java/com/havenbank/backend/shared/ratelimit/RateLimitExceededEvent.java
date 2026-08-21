package com.havenbank.backend.shared.ratelimit;

/**
 * Published when a request is throttled, so the audit module can record it (FR-6.5).
 *
 * <p>The source IP, user agent and correlation id are captured here, on the request thread, because
 * the audit listener runs {@code @Async}: by the time it executes, the thread-locals those values
 * normally come from are empty. Carrying them on the event is what keeps them on the audit row.
 */
public record RateLimitExceededEvent(String path, String tier, String clientKey,
                                     String sourceIp, String userAgent, String correlationId) {
}