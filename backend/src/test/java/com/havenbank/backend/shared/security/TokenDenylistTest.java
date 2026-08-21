package com.havenbank.backend.shared.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Revoked token ids are stored only for the token's remaining lifetime (FR-1.9).
 */
@ExtendWith(MockitoExtension.class)
class TokenDenylistTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private TokenDenylist denylist;

    @Test
    void storesJtiUnderItsRemainingLifetime() {
        when(redis.opsForValue()).thenReturn(valueOps);

        denylist.denylist("abc-123", Duration.ofMinutes(4));

        verify(valueOps).set(eq("denylist:jti:abc-123"), eq("1"), eq(Duration.ofMinutes(4)));
    }

    @Test
    void ignoresNullJti() {
        denylist.denylist(null, Duration.ofMinutes(4));
        verify(redis, never()).opsForValue();
    }

    @Test
    void ignoresExpiredOrZeroTtlSoNothingIsStoredForever() {
        denylist.denylist("abc-123", Duration.ZERO);
        denylist.denylist("abc-123", Duration.ofSeconds(-1));
        verify(redis, never()).opsForValue();
    }

    @Test
    void reportsDenylistedWhenKeyPresent() {
        when(redis.hasKey("denylist:jti:abc-123")).thenReturn(true);
        assertThat(denylist.isDenylisted("abc-123")).isTrue();
    }

    @Test
    void reportsNotDenylistedWhenKeyAbsent() {
        when(redis.hasKey("denylist:jti:abc-123")).thenReturn(false);
        assertThat(denylist.isDenylisted("abc-123")).isFalse();
    }

    @Test
    void treatsNullJtiAsNotDenylisted() {
        assertThat(denylist.isDenylisted(null)).isFalse();
        verify(redis, never()).hasKey(any());
    }
}
