package com.havenbank.backend.money.service;

import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Step-up re-authentication for high-value transfers (FR-3.9). Issues an email OTP, and on
 * verification grants a short-lived "elevation" recorded in Redis, which the transfer path checks.
 */
@Service
@RequiredArgsConstructor
public class StepUpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration ELEVATION_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final NotificationService notificationService;

    public void issueChallenge(UUID userId, String email, String name) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(userId), code, OTP_TTL);
        notificationService.send(new NotificationMessage(
                email, name, NotificationType.STEP_UP_OTP, Map.of("code", code)));
    }

    /**
     * Verify a step-up code; on success record an elevation for this user.
     */
    public boolean verify(UUID userId, String code) {
        String expected = redis.opsForValue().get(codeKey(userId));
        if (expected != null && expected.equals(code)) {
            redis.delete(codeKey(userId));
            redis.opsForValue().set(elevationKey(userId), "1", ELEVATION_TTL);
            return true;
        }
        return false;
    }

    public boolean isElevated(UUID userId) {
        return Boolean.TRUE.equals(redis.hasKey(elevationKey(userId)));
    }

    /**
     * Consume the elevation so it applies to a single high-value transfer.
     */
    public void consumeElevation(UUID userId) {
        redis.delete(elevationKey(userId));
    }

    private String codeKey(UUID userId) {
        return "stepup:code:" + userId;
    }

    private String elevationKey(UUID userId) {
        return "stepup:elevated:" + userId;
    }
}
