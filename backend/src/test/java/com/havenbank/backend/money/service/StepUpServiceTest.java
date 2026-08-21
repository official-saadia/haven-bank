package com.havenbank.backend.money.service;

import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Step-up re-authentication for high-value transfers (FR-3.9).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StepUpServiceTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CODE_KEY = "stepup:code:" + USER;
    private static final String ELEVATION_KEY = "stepup:elevated:" + USER;

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private StepUpService stepUpService;

    @Test
    void emailsASixDigitChallengeAndStoresIt() {
        when(redis.opsForValue()).thenReturn(valueOps);

        stepUpService.issueChallenge(USER, "alice@example.com", "Alice");

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(CODE_KEY), stored.capture(), any(Duration.class));
        assertThat(stored.getValue()).hasSize(6).containsOnlyDigits();

        ArgumentCaptor<NotificationMessage> sent = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).send(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.STEP_UP_OTP);
    }

    @Test
    void neverPutsTheCodeInTheReturnValue() {
        when(redis.opsForValue()).thenReturn(valueOps);
        // issueChallenge is void by design: the code leaves only by email (FR-1.8b).
        stepUpService.issueChallenge(USER, "alice@example.com", "Alice");
        verify(notificationService).send(any(NotificationMessage.class));
    }

    @Test
    void grantsElevationOnTheCorrectCodeAndConsumesTheChallenge() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CODE_KEY)).thenReturn("123456");

        assertThat(stepUpService.verify(USER, "123456")).isTrue();

        verify(redis).delete(CODE_KEY);
        verify(valueOps).set(eq(ELEVATION_KEY), eq("1"), any(Duration.class));
    }

    @Test
    void refusesElevationOnAWrongCode() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CODE_KEY)).thenReturn("123456");

        assertThat(stepUpService.verify(USER, "999999")).isFalse();

        verify(redis, never()).delete(CODE_KEY);
        verify(valueOps, never()).set(eq(ELEVATION_KEY), any(), any(Duration.class));
    }

    @Test
    void refusesElevationWhenNoChallengeIsOutstanding() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CODE_KEY)).thenReturn(null);

        assertThat(stepUpService.verify(USER, "123456")).isFalse();
    }

    @Test
    void reportsElevationWhileTheGrantIsLive() {
        when(redis.hasKey(ELEVATION_KEY)).thenReturn(true);
        assertThat(stepUpService.isElevated(USER)).isTrue();
    }

    @Test
    void reportsNoElevationOnceItHasExpired() {
        when(redis.hasKey(ELEVATION_KEY)).thenReturn(false);
        assertThat(stepUpService.isElevated(USER)).isFalse();
    }

    @Test
    void elevationIsSingleUseSoItCannotCoverASecondTransfer() {
        stepUpService.consumeElevation(USER);
        verify(redis).delete(ELEVATION_KEY);
    }
}
