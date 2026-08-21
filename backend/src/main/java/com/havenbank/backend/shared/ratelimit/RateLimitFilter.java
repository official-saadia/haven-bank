package com.havenbank.backend.shared.ratelimit;

import com.havenbank.backend.shared.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Applies {@link RateLimiter} per {@link RateLimitTier} before the request reaches the application.
 * Throttled requests get {@code 429} with a {@code Retry-After} header and an RFC 7807 body, and an
 * event is published for auditing.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final TierResolver tierResolver;
    private final ApplicationEventPublisher events;
    private final RateLimitProperties properties;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        RateLimitTier tier = tierResolver.resolve(resolveHandlerMethod(request),
                request.getRequestURI(), request.getMethod());
        if (tier == null) {
            // No matched handler and no framework-endpoint config entry - most commonly a path
            // probe against a nonexistent endpoint (404), which RateLimitCoverageVerifier cannot
            // catch since there is no handler method to scan. Defaulting to STANDARD, rather than
            // passing the request through unlimited, means an unmatched path still costs an
            // attacker something instead of being a free, uncapped resource-consumption vector.
            tier = RateLimitTier.STANDARD;
        }
        String key = keyFor(tier, request);
        RateLimiter.Decision decision = rateLimiter.check(tier, key);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        events.publishEvent(new RateLimitExceededEvent(request.getRequestURI(), tier.name(), key,
                clientIp(request), request.getHeader(HttpHeaders.USER_AGENT), MDC.get(CorrelationIdFilter.MDC_KEY)));
        writeTooManyRequests(response, decision.retryAfterSeconds());
    }

    /**
     * Resolves the matched controller method, if any, so {@link TierResolver} can read a
     * {@link RateLimited} annotation. Calls {@link RequestMappingHandlerMapping} directly rather
     * than the (now deprecated-for-removal, without replacement, as of Spring Framework 7.0)
     * {@code HandlerMappingIntrospector} - the mapping's own {@code getHandler} is all that
     * wrapper ever did for a single mapping. Returns {@code null} for framework-owned endpoints
     * (Spring Security, the Authorization Server) that have no {@link HandlerMethod} - those fall
     * back to {@link TierResolver}'s path-based config. Failures here are non-fatal: the request
     * still gets a tier via that fallback, it just won't see an annotation.
     */
    private HandlerMethod resolveHandlerMethod(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = requestMappingHandlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
                return handlerMethod;
            }
        } catch (Exception ex) {
            logger.debug("Could not resolve handler method for rate-limit tiering", ex);
        }
        return null;
    }

    private String keyFor(RateLimitTier tier, HttpServletRequest request) {
        if (properties.forTier(tier).getKey() == KeyType.SUBJECT) {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
                return "t:" + DigestUtils.md5DigestAsHex(auth.substring(7).getBytes(StandardCharsets.UTF_8));
            }
        }
        return "ip:" + clientIp(request);
    }

    /**
     * {@code X-Forwarded-For} is trusted here because the app sits behind a load balancer, which
     * is the only path a request can take to reach it - the load balancer overwrites any
     * client-supplied value with the real chain, so this header cannot be spoofed by the caller.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        response.getWriter().write("""
                {"type":"https://errors.havenbank.example/rate-limited",\
                "title":"Too many requests","status":%d,\
                "detail":"Rate limit exceeded. Retry after %ds.",\
                "correlationId":"%s"}\
                """.formatted(HttpStatus.TOO_MANY_REQUESTS.value(), retryAfter,
                correlationId == null ? "" : correlationId));
    }
}