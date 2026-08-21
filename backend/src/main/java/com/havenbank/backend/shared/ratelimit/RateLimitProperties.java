package com.havenbank.backend.shared.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Externalised rate-limit configuration (FR-6.6). Thresholds live here rather than in code so an
 * operator can tighten them during a credential-stuffing incident by changing configuration,
 * without waiting for a build.
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Map<RateLimitTier, Tier> tiers = new EnumMap<>(RateLimitTier.class);
    private Backoff backoff = new Backoff();
    /**
     * Ant-style path patterns for endpoints with no annotatable controller method - the embedded
     * Authorization Server ({@code /oauth2/**}, {@code /userinfo}) - keyed by the tier they
     * resolve to. Application controllers use {@link RateLimited} instead; see {@link TierResolver}.
     * Kept in configuration, not code, for the same reason as the thresholds above: an operator
     * can add or move a framework path here without a rebuild.
     */
    private Map<RateLimitTier, List<String>> frameworkEndpoints = new EnumMap<>(RateLimitTier.class);

    /**
     * Settings for one sensitivity band.
     */
    public static class Tier {
        /**
         * Requests permitted per window.
         */
        private int limit = 100;
        /**
         * Length of the fixed window.
         */
        private Duration window = Duration.ofMinutes(1);
        /**
         * What the limit counts against.
         */
        private KeyType key = KeyType.SUBJECT;
        /**
         * Whether repeated violations earn an escalating block.
         */
        private boolean backoff = false;
        /**
         * On a Redis outage: deny (true) or allow (false). See NFR-5.2.
         */
        private boolean failClosed = false;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public KeyType getKey() {
            return key;
        }

        public void setKey(KeyType key) {
            this.key = key;
        }

        public boolean isBackoff() {
            return backoff;
        }

        public void setBackoff(boolean backoff) {
            this.backoff = backoff;
        }

        public boolean isFailClosed() {
            return failClosed;
        }

        public void setFailClosed(boolean failClosed) {
            this.failClosed = failClosed;
        }
    }

    /**
     * Escalation applied to tiers with {@code backoff} enabled.
     */
    public static class Backoff {
        /**
         * First block after the limit is breached; doubles on each further violation.
         */
        private Duration base = Duration.ofSeconds(2);
        /**
         * Ceiling, so an attacker cannot lock an IP out indefinitely.
         */
        private Duration max = Duration.ofMinutes(15);
        /**
         * How long violations are remembered when computing the escalation.
         */
        private Duration violationWindow = Duration.ofHours(1);

        public Duration getBase() {
            return base;
        }

        public void setBase(Duration base) {
            this.base = base;
        }

        public Duration getMax() {
            return max;
        }

        public void setMax(Duration max) {
            this.max = max;
        }

        public Duration getViolationWindow() {
            return violationWindow;
        }

        public void setViolationWindow(Duration violationWindow) {
            this.violationWindow = violationWindow;
        }
    }

    /**
     * Settings for a tier, falling back to conservative defaults if it is not configured.
     */
    public Tier forTier(RateLimitTier tier) {
        return tiers.computeIfAbsent(tier, t -> new Tier());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<RateLimitTier, Tier> getTiers() {
        return tiers;
    }

    public void setTiers(Map<RateLimitTier, Tier> tiers) {
        this.tiers = tiers;
    }

    public Backoff getBackoff() {
        return backoff;
    }

    public void setBackoff(Backoff backoff) {
        this.backoff = backoff;
    }

    public Map<RateLimitTier, List<String>> getFrameworkEndpoints() {
        return frameworkEndpoints;
    }

    public void setFrameworkEndpoints(Map<RateLimitTier, List<String>> frameworkEndpoints) {
        this.frameworkEndpoints = frameworkEndpoints;
    }
}
