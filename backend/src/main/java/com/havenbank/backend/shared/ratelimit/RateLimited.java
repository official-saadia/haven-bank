package com.havenbank.backend.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link RateLimitTier} for an application-owned controller method. Framework-owned
 * endpoints (Spring Security's {@code /login}, the Authorization Server's {@code /oauth2/**},
 * {@code /userinfo}) have no annotatable handler method and are still resolved by the path list in
 * {@link TierResolver}.
 *
 * <p>An endpoint with neither this annotation nor a {@link TierResolver} path entry is unlimited.
 * {@code RateLimitCoverageTest} fails the build if a mutating endpoint is missing both.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {
    RateLimitTier value();
}
