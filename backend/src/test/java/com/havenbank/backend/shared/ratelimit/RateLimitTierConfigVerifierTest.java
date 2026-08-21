package com.havenbank.backend.shared.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards against a tier silently falling back to {@link RateLimitProperties.Tier}'s bare defaults
 * (100/min, no backoff, fail-open) because its {@code app.ratelimit.tiers} block was left out of
 * configuration.
 */
class RateLimitTierConfigVerifierTest {

    @Test
    void passesWhenEveryTierIsConfigured() {
        RateLimitProperties properties = new RateLimitProperties();
        Map<RateLimitTier, RateLimitProperties.Tier> tiers = new EnumMap<>(RateLimitTier.class);
        for (RateLimitTier tier : RateLimitTier.values()) {
            tiers.put(tier, new RateLimitProperties.Tier());
        }
        properties.setTiers(tiers);

        assertThatCode(() -> new RateLimitTierConfigVerifier(properties).verifyEveryTierIsConfigured())
                .doesNotThrowAnyException();
    }

    @Test
    void failsStartupWhenATierIsMissingItsConfigurationBlock() {
        RateLimitProperties properties = new RateLimitProperties();
        Map<RateLimitTier, RateLimitProperties.Tier> tiers = new EnumMap<>(RateLimitTier.class);
        tiers.put(RateLimitTier.SENSITIVE, new RateLimitProperties.Tier());
        tiers.put(RateLimitTier.STANDARD, new RateLimitProperties.Tier());
        // CRITICAL deliberately left out - the case the verifier exists to catch.
        properties.setTiers(tiers);

        // Only asserts that CRITICAL is flagged. Deliberately does not assert other tier names
        // are absent from the message - the explanation text is free to mention SENSITIVE or
        // STANDARD as examples of impact without that meaning they were reported as missing.
        assertThatThrownBy(() -> new RateLimitTierConfigVerifier(properties).verifyEveryTierIsConfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL");
    }

    @Test
    void failsStartupWithAllTiersMissingWhenConfigurationIsEntirelyAbsent() {
        RateLimitProperties properties = new RateLimitProperties();
        // No setTiers() call - simulates app.ratelimit.tiers being absent from every active
        // profile entirely, not just one entry within it.

        assertThatThrownBy(() -> new RateLimitTierConfigVerifier(properties).verifyEveryTierIsConfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL")
                .hasMessageContaining("SENSITIVE")
                .hasMessageContaining("STANDARD");
    }
}