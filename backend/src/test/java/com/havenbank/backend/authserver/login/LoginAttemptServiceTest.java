package com.havenbank.backend.authserver.login;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Progressive throttling: escalating locks rather than one long block (FR-1.6).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    private static final String EMAIL = "alice@example.com";
    private static final String FAIL_KEY = "login:fail:alice@example.com";
    private static final String LOCK_KEY = "login:lock:alice@example.com";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private LoginAttemptService service;

    @Test
    void doesNotLockBelowTheThreshold() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(3L);

        assertThat(service.recordFailure(EMAIL)).isFalse();
        verify(valueOps, never()).set(eq(LOCK_KEY), anyString(), any(Duration.class));
    }

    @Test
    void setsTheFailureWindowOnlyOnTheFirstFailure() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(1L);

        service.recordFailure(EMAIL);

        verify(redis).expire(eq(FAIL_KEY), any(Duration.class));
    }

    @Test
    void locksOnceTheThresholdIsReached() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(5L);

        assertThat(service.recordFailure(EMAIL)).isTrue();
        verify(valueOps).set(eq(LOCK_KEY), eq("1"), eq(Duration.ofMillis(30_000)));
    }

    @Test
    void escalatesTheLockOnEachFurtherFailure() {
        when(redis.opsForValue()).thenReturn(valueOps);

        when(valueOps.increment(FAIL_KEY)).thenReturn(6L);
        service.recordFailure(EMAIL);
        verify(valueOps).set(eq(LOCK_KEY), eq("1"), eq(Duration.ofMillis(60_000)));

        when(valueOps.increment(FAIL_KEY)).thenReturn(7L);
        service.recordFailure(EMAIL);
        verify(valueOps).set(eq(LOCK_KEY), eq("1"), eq(Duration.ofMillis(120_000)));
    }

    @Test
    void capsTheLockSoAnAttackerCannotLockAnAccountOutIndefinitely() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_KEY)).thenReturn(50L);

        service.recordFailure(EMAIL);

        verify(valueOps).set(eq(LOCK_KEY), eq("1"), eq(Duration.ofMillis(30 * 60_000L)));
    }

    @Test
    void reportsLockedWhileTheLockKeyHasTimeToLive() {
        when(redis.getExpire(LOCK_KEY, TimeUnit.MILLISECONDS)).thenReturn(4_000L);

        assertThat(service.isLocked(EMAIL)).isTrue();
        assertThat(service.retryAfterSeconds(EMAIL)).isEqualTo(4L);
    }

    @Test
    void reportsUnlockedWhenNoLockKeyExists() {
        when(redis.getExpire(LOCK_KEY, TimeUnit.MILLISECONDS)).thenReturn(-2L);

        assertThat(service.isLocked(EMAIL)).isFalse();
        assertThat(service.retryAfterSeconds(EMAIL)).isZero();
    }

    @Test
    void clearsBothCountersOnSuccessfulLogin() {
        service.reset(EMAIL);

        verify(redis).delete(FAIL_KEY);
        verify(redis).delete(LOCK_KEY);
    }

    @Test
    void toleratesNullEmail() {
        assertThat(service.isLocked(null)).isFalse();
        assertThat(service.recordFailure(null)).isFalse();
        service.reset(null);
        verify(redis, never()).delete(anyString());
    }
}
