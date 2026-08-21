package com.havenbank.backend.shared.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tiered limiting (FR-6.1–6.4) and graceful degradation: auth fails closed, reads fail open
 * (NFR-5.2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RateLimitProperties properties;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.getTiers().put(RateLimitTier.CRITICAL, tier(5, KeyType.IP, true, true));
        properties.getTiers().put(RateLimitTier.SENSITIVE, tier(20, KeyType.SUBJECT, false, true));
        properties.getTiers().put(RateLimitTier.STANDARD, tier(100, KeyType.SUBJECT, false, false));
        rateLimiter = new RateLimiter(redis, properties);
    }

    private static RateLimitProperties.Tier tier(int limit, KeyType key, boolean backoff, boolean failClosed) {
        RateLimitProperties.Tier t = new RateLimitProperties.Tier();
        t.setLimit(limit);
        t.setWindow(Duration.ofMinutes(1));
        t.setKey(key);
        t.setBackoff(backoff);
        t.setFailClosed(failClosed);
        return t;
    }

    @SuppressWarnings("unchecked")
    private void windowReturns(Long count) {
        when(redis.execute(any(RedisScript.class), any(List.class), any())).thenReturn(count);
    }

    @Test
    void allowsARequestInsideTheWindow() {
        windowReturns(1L);

        RateLimiter.Decision decision = rateLimiter.check(RateLimitTier.STANDARD, "user-1");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    void allowsTheRequestExactlyOnTheLimit() {
        windowReturns(100L);

        assertThat(rateLimiter.check(RateLimitTier.STANDARD, "user-1").allowed()).isTrue();
    }

    @Test
    void deniesOnceTheLimitIsExceeded() {
        windowReturns(101L);
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(30_000L);

        RateLimiter.Decision decision = rateLimiter.check(RateLimitTier.STANDARD, "user-1");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(30L);
    }

    @Test
    void appliesAnEscalatingBlockOnTheCriticalTier() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(-2L);
        windowReturns(6L);

        RateLimiter.Decision decision = rateLimiter.check(RateLimitTier.CRITICAL, "1.2.3.4");

        assertThat(decision.allowed()).isFalse();
        verify(valueOps).set(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void shortCircuitsWhileACriticalKeyIsAlreadyBlocked() {
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(8_000L);

        RateLimiter.Decision decision = rateLimiter.check(RateLimitTier.CRITICAL, "1.2.3.4");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(8L);
    }

    @Test
    void failsClosedForAuthenticationWhenRedisIsUnavailable() {
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new QueryTimeoutException("redis down"));

        RateLimiter.Decision decision = rateLimiter.check(RateLimitTier.CRITICAL, "1.2.3.4");

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsOpenForReadsWhenRedisIsUnavailable() {
        when(redis.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new QueryTimeoutException("redis down"));

        RateLimiter.Decision decision = rateLimiter.check(RateLimitTier.STANDARD, "user-1");

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsClosedForSensitiveWritesWhenRedisIsUnavailable() {
        when(redis.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new QueryTimeoutException("redis down"));

        assertThat(rateLimiter.check(RateLimitTier.SENSITIVE, "user-1").allowed()).isFalse();
    }

    @Test
    void unconfiguredTierFallsBackToConservativeDefaults() {
        // A tier missing from configuration must still be limited, and must not fail closed on a
        // read path - silence in the config file should never mean "unlimited".
        RateLimitProperties bare = new RateLimitProperties();
        RateLimitProperties.Tier fallback = bare.forTier(RateLimitTier.CRITICAL);

        assertThat(fallback.getLimit()).isPositive();
        assertThat(fallback.getWindow()).isEqualTo(Duration.ofMinutes(1));
    }
}
