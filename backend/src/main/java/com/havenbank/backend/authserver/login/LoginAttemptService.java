package com.havenbank.backend.authserver.login;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Tracks failed login attempts per email and applies a <strong>progressive</strong> lock (FR-1.6):
 * once the threshold is passed, each further failure escalates the lock duration (doubling, capped).
 * State is Redis-backed with TTLs, so locks expire automatically and are correct across instances.
 * Separating short escalating locks from a permanent block avoids the denial-of-service pitfall noted
 * in the requirements' open points.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int THRESHOLD = 5;
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);
    private static final long BASE_LOCK_MS = 30_000;
    private static final long MAX_LOCK_MS = 30 * 60_000;

    private final StringRedisTemplate redis;

    public boolean isLocked(String email) {
        if (email == null) return false;
        Long ttl = redis.getExpire(lockKey(email), TimeUnit.MILLISECONDS);
        return ttl != null && ttl > 0;
    }

    public long retryAfterSeconds(String email) {
        Long ttl = redis.getExpire(lockKey(email), TimeUnit.MILLISECONDS);
        return ttl == null || ttl < 0 ? 0 : (ttl + 999) / 1000;
    }

    /**
     * Record a failed attempt; returns {@code true} if the account is now (still) locked.
     */
    public boolean recordFailure(String email) {
        if (email == null) return false;
        Long count = redis.opsForValue().increment(failKey(email));
        if (count != null && count == 1L) {
            redis.expire(failKey(email), FAIL_WINDOW);
        }
        long c = count == null ? 0 : count;
        if (c >= THRESHOLD) {
            long over = c - THRESHOLD;
            long lockMs = Math.min(BASE_LOCK_MS << Math.min(over, 20), MAX_LOCK_MS);
            redis.opsForValue().set(lockKey(email), "1", Duration.ofMillis(lockMs));
            return true;
        }
        return false;
    }

    public void reset(String email) {
        if (email == null) return;
        redis.delete(failKey(email));
        redis.delete(lockKey(email));
    }

    private String failKey(String email) {
        return "login:fail:" + email.toLowerCase();
    }

    private String lockKey(String email) {
        return "login:lock:" + email.toLowerCase();
    }
}
