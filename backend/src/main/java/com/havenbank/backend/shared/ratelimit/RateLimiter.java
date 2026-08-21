package com.havenbank.backend.shared.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed limiter. Uses an atomic INCR+PEXPIRE fixed window per key; for backoff tiers, repeated
 * violations set an escalating (doubling) block so brute-force attempts are progressively slowed
 * (FR-6.2). State lives in Redis so limits are correct across horizontally scaled instances (FR-6.4).
 */
@Slf4j
@Component
public class RateLimiter {

    /**
     * Atomically increments the window counter and sets its TTL on first hit; returns the count.
     */
    private static final RedisScript<Long> INCR_WINDOW = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c", Long.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public Decision check(RateLimitTier tier, String key) {
        RateLimitProperties.Tier cfg = properties.forTier(tier);
        RateLimitProperties.Backoff bo = properties.getBackoff();
        long windowMs = cfg.getWindow().toMillis();
        String prefix = "rl:" + tier.name() + ":" + key;
        try {
            if (cfg.isBackoff()) {
                Long blockTtl = redis.getExpire(prefix + ":block", TimeUnit.MILLISECONDS);
                if (blockTtl != null && blockTtl > 0) {
                    return deny(blockTtl);
                }
            }
            Long count = redis.execute(INCR_WINDOW, List.of(prefix + ":win"), String.valueOf(windowMs));
            if (count != null && count <= cfg.getLimit()) {
                return new Decision(true, 0);
            }
            if (cfg.isBackoff()) {
                Long viol = redis.execute(INCR_WINDOW, List.of(prefix + ":viol"),
                        String.valueOf(bo.getViolationWindow().toMillis()));
                long exp = Math.min(viol == null ? 0 : viol - 1, 20);
                long blockMs = Math.min(bo.getBase().toMillis() << exp, bo.getMax().toMillis());
                redis.opsForValue().set(prefix + ":block", "1", Duration.ofMillis(blockMs));
                return deny(blockMs);
            }
            Long ttl = redis.getExpire(prefix + ":win", TimeUnit.MILLISECONDS);
            return deny(ttl != null && ttl > 0 ? ttl : windowMs);
        } catch (Exception ex) {
            // Redis unavailable: fail closed for auth/sensitive tiers, open for reads (NFR-5.2).
            log.warn("Rate limiter backend error; failing {}", cfg.isFailClosed() ? "closed" : "open", ex);
            return cfg.isFailClosed() ? new Decision(false, 5) : new Decision(true, 0);
        }
    }

    private Decision deny(long ttlMillis) {
        return new Decision(false, Math.max(1, (ttlMillis + 999) / 1000));
    }
}
