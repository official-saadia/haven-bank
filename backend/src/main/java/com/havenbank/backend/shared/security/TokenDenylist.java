package com.havenbank.backend.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed denylist of revoked access-token ids ({@code jti}). Entries are stored only for the
 * remaining lifetime of the token, so the set stays small and self-cleans (FR-1.9).
 */
@Component
@RequiredArgsConstructor
public class TokenDenylist {

    private final StringRedisTemplate redis;

    public void denylist(String jti, Duration ttl) {
        if (jti == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(key(jti), "1", ttl);
    }

    public boolean isDenylisted(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(key(jti)));
    }

    private String key(String jti) {
        return "denylist:jti:" + jti;
    }
}
