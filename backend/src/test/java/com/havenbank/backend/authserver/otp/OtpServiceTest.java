package com.havenbank.backend.authserver.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login OTP: single-use, bounded attempts, never predictable (FR-1.8, 1.8a, 1.8b).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    private static final String EMAIL = "Alice@Example.com";
    private static final String CODE_KEY = "otp:login:alice@example.com";
    private static final String ATTEMPTS_KEY = "otp:login:attempts:alice@example.com";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "ttl", Duration.ofMinutes(5));
        ReflectionTestUtils.setField(otpService, "length", 6);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 5);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void issuesANumericCodeOfTheConfiguredLength() {
        String code = otpService.issue(EMAIL);

        assertThat(code).hasSize(6).containsOnlyDigits();
        verify(valueOps).set(eq(CODE_KEY), eq(code), any(Duration.class));
        verify(valueOps).set(eq(ATTEMPTS_KEY), eq("0"), any(Duration.class));
    }

    @Test
    void issuesADifferentCodeEachTime() {
        // Not a proof of randomness, but catches a constant or sequential generator.
        assertThat(otpService.issue(EMAIL)).isNotNull();
        boolean allIdentical = true;
        String first = otpService.issue(EMAIL);
        for (int i = 0; i < 20 && allIdentical; i++) {
            if (!first.equals(otpService.issue(EMAIL))) {
                allIdentical = false;
            }
        }
        assertThat(allIdentical).isFalse();
    }

    @Test
    void acceptsTheCorrectCodeAndBurnsItSoItCannotBeReplayed() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(1L);

        assertThat(otpService.verify(EMAIL, "123456")).isTrue();

        verify(redis).delete(CODE_KEY);
        verify(redis).delete(ATTEMPTS_KEY);
    }

    @Test
    void rejectsAWrongCodeWithoutBurningTheChallenge() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(2L);

        assertThat(otpService.verify(EMAIL, "000000")).isFalse();

        verify(redis, org.mockito.Mockito.never()).delete(anyString());
    }

    @Test
    void rejectsWhenNoChallengeWasIssuedOrItExpired() {
        when(valueOps.get(CODE_KEY)).thenReturn(null);

        assertThat(otpService.verify(EMAIL, "123456")).isFalse();
    }

    @Test
    void burnsTheChallengeOnceTheAttemptLimitIsExceeded() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(6L);

        // Even the correct code fails once the bound is passed.
        assertThat(otpService.verify(EMAIL, "123456")).isFalse();

        verify(redis).delete(CODE_KEY);
        verify(redis).delete(ATTEMPTS_KEY);
    }

    @Test
    void treatsEmailCaseInsensitively() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(1L);

        assertThat(otpService.verify("ALICE@EXAMPLE.COM", "123456")).isTrue();
    }
}
