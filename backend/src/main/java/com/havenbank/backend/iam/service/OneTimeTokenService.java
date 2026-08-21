package com.havenbank.backend.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and consumes single-use, expiring tokens for email verification and password reset. State
 * lives in Redis with a TTL, so tokens self-expire (no cleanup job) and throwaway secrets never
 * touch the durable store (FR-1.1a, FR-1.11). Tokens are opaque, high-entropy random values; only a
 * hash of the token could additionally be stored, but the value itself is never logged or returned
 * beyond the outbound email link.
 */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private final StringRedisTemplate redis;

    @Value("${app.verification.email-token-ttl}")
    private Duration emailTokenTtl;

    @Value("${app.verification.password-reset-token-ttl}")
    private Duration passwordResetTtl;

    /**
     * Create a new token bound to the given user and store it with the type's TTL.
     *
     * @return the opaque token to embed in the outbound link
     */
    public String issue(OneTimeTokenType type, UUID userId) {
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        redis.opsForValue().set(type.keyPrefix() + token, userId.toString(), ttlFor(type));
        return token;
    }

    /**
     * Atomically validate and consume a token. Returns the bound user id if the token exists, then
     * deletes it so it cannot be reused (single-use, FR-1.1a/1.11).
     */
    public Optional<UUID> consume(OneTimeTokenType type, String token) {
        String key = type.keyPrefix() + token;
        String userId = redis.opsForValue().getAndDelete(key);
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    private Duration ttlFor(OneTimeTokenType type) {
        return type == OneTimeTokenType.EMAIL_VERIFICATION ? emailTokenTtl : passwordResetTtl;
    }
}
