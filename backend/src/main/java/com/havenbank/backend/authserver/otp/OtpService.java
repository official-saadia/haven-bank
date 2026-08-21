package com.havenbank.backend.authserver.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Issues and verifies the email login OTP. State lives in Redis with a short TTL, is single-use, and
 * is bounded to a small number of verification attempts before it is burned and must be re-issued
 * (FR-1.8/1.8a/1.8b). Codes are drawn from a {@link SecureRandom} and are never returned by any API
 * or written to logs.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${app.otp.ttl:PT5M}")
    private Duration ttl;

    @Value("${app.otp.length:6}")
    private int length;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    /**
     * Generate, store and return a fresh code for the given subject (the caller emails it).
     */
    public String issue(String email) {
        String code = randomDigits(length);
        redis.opsForValue().set(codeKey(email), code, ttl);
        redis.opsForValue().set(attemptsKey(email), "0", ttl);
        return code;
    }

    /**
     * Verify a submitted code. Returns {@code true} on a match (consuming the code). On mismatch the
     * attempt counter is incremented; once it reaches the limit the challenge is burned.
     */
    public boolean verify(String email, String submitted) {
        String expected = redis.opsForValue().get(codeKey(email));
        if (expected == null) {
            return false; // expired or never issued
        }
        Long attempts = redis.opsForValue().increment(attemptsKey(email));
        if (attempts != null && attempts > maxAttempts) {
            burn(email);
            return false;
        }
        if (expected.equals(submitted)) {
            burn(email);
            return true;
        }
        return false;
    }

    private void burn(String email) {
        redis.delete(codeKey(email));
        redis.delete(attemptsKey(email));
    }

    private String randomDigits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String codeKey(String email) {
        return "otp:login:" + email.toLowerCase();
    }

    private String attemptsKey(String email) {
        return "otp:login:attempts:" + email.toLowerCase();
    }
}
