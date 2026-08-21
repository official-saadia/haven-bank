package com.havenbank.backend.shared.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

/**
 * Maps a request to its {@link RateLimitTier}, or {@code null} if it is not limited.
 *
 * <p>Two sources, checked in order:
 * <ol>
 *   <li>{@link RateLimited} on the resolved controller method — every application controller,
 *   including {@code /login} and {@code /login/otp}. The tier lives next to the code it governs,
 *   and a missing annotation on a new mutating endpoint is caught by
 *   {@code RateLimitCoverageTest} rather than drifting silently.</li>
 *   <li>{@code app.ratelimit.framework-endpoints} in configuration, for the embedded
 *   Authorization Server's endpoints ({@code /oauth2/**}, {@code /userinfo}), which have no
 *   controller method in this codebase to annotate. Configuration rather than a hardcoded list
 *   for the same reason as the thresholds themselves (FR-6.6): addable/movable without a
 *   rebuild.</li>
 * </ol>
 *
 * <p>The method matters as much as the path. Throttling authentication means throttling
 * <em>attempts</em> — the requests that submit a credential — not the GET that renders the form.
 * {@code LoginController} and {@code OtpController} reflect that directly: their GET handlers are
 * annotated {@link RateLimitTier#STANDARD}, their POST handlers {@link RateLimitTier#CRITICAL}.
 */
@Component
@RequiredArgsConstructor
public class TierResolver {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final RateLimitProperties properties;

    /**
     * Preferred entry point: resolves from the {@link RateLimited} annotation on the matched
     * controller method first, falling back to {@code app.ratelimit.framework-endpoints} for
     * framework endpoints.
     */
    public RateLimitTier resolve(HandlerMethod handler, String path, String method) {
        if (handler != null) {
            RateLimited annotation = handler.getMethodAnnotation(RateLimited.class);
            if (annotation != null) {
                return annotation.value();
            }
        }
        return resolve(path, method);
    }

    /**
     * Path-only resolution against {@code app.ratelimit.framework-endpoints}, for requests the
     * filter couldn't map to a {@link HandlerMethod}. STANDARD entries match on any method (they
     * cover reads); other tiers only match on a mutating method, mirroring the annotation side.
     */
    public RateLimitTier resolve(String path, String method) {
        boolean submitting = !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
        for (Map.Entry<RateLimitTier, List<String>> entry : properties.getFrameworkEndpoints().entrySet()) {
            RateLimitTier tier = entry.getKey();
            List<String> patterns = entry.getValue();
            boolean eligible = tier == RateLimitTier.STANDARD || submitting;
            if (eligible && matchesAny(patterns, path)) {
                return tier;
            }
        }
        return null;
    }

    private boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(p -> matcher.match(p, path));
    }
}