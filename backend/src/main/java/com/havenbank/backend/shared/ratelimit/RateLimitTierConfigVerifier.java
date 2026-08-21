package com.havenbank.backend.shared.ratelimit;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails application startup if any {@link RateLimitTier} has no explicit entry under
 * {@code app.ratelimit.tiers} in configuration.
 *
 * <p>{@link RateLimitProperties#forTier} falls back to a bare {@code new Tier()} for an
 * unconfigured tier - Java field defaults ({@code limit=100}, {@code failClosed=false}), not a
 * copy of any real tier's settings. For CRITICAL or SENSITIVE, a missing config block would
 * silently loosen the limit and flip fail-open instead of fail-closed on a Redis outage (NFR-5.2)
 * - a real security regression from a config typo, with no error and no loud failure. This check,
 * in the style of {@code RateLimitCoverageVerifier}, converts that silent gap into a startup
 * failure naming exactly which tier is missing.
 */
@Component
class RateLimitTierConfigVerifier {

    private final RateLimitProperties properties;

    RateLimitTierConfigVerifier(RateLimitProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verifyEveryTierIsConfigured() {
        List<String> missing = new ArrayList<>();

        for (RateLimitTier tier : RateLimitTier.values()) {
            if (!properties.getTiers().containsKey(tier)) {
                missing.add(tier.name());
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("""
                    
                    Rate-limit tier configuration check failed, so the application will not start.
                    
                    Every RateLimitTier must have an explicit entry under app.ratelimit.tiers in
                    configuration. Without one, RateLimitProperties.forTier() falls back to bare
                    Java defaults (limit=100, failClosed=false) rather than that tier's intended
                    settings - a missing CRITICAL or SENSITIVE block would silently loosen the
                    limit and fail open on a Redis outage instead of closed.
                    
                    Tiers missing from app.ratelimit.tiers:
                      %s
                    
                    Fix by adding a tiers.<name> block for each one above in application.yaml.
                    """.formatted(String.join("\n  ", missing)));
        }
    }
}