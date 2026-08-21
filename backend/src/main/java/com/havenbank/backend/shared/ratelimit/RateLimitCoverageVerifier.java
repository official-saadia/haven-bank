package com.havenbank.backend.shared.ratelimit;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fails application startup if any of our own controller endpoints has no {@link RateLimited}
 * tier. An unlimited endpoint is a silent gap - the request is served as normal, so nothing about
 * it looks wrong until it is abused - and that makes it the kind of defect most likely to go
 * unnoticed until it matters. Checking at startup, in the style of
 * {@code SmtpEmailSender.verifyConfigured()}, surfaces the gap the moment a developer runs the
 * application, rather than after a deploy.
 *
 * <p>Scope is limited to handler methods declared in our own {@code com.havenbank.backend}
 * package. That excludes framework/library-owned handlers - Boot's error controller, springdoc's
 * OpenAPI/Swagger endpoints, actuator if it is ever added - which are not ours to annotate and are
 * not part of this coverage guarantee. The Authorization Server's own endpoints
 * ({@code /oauth2/**}, {@code /userinfo}) are not registered as {@link HandlerMethod}s at all, so
 * they never reach this check; they are covered separately via
 * {@code app.ratelimit.framework-endpoints} in {@link TierResolver}.
 */
@Component
class RateLimitCoverageVerifier {

    private static final String APP_PACKAGE_PREFIX = "com.havenbank.backend";

    private final RequestMappingHandlerMapping handlerMapping;

    RateLimitCoverageVerifier(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @PostConstruct
    void verifyEveryEndpointIsTiered() {
        List<String> uncovered = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Class<?> declaringClass = handler.getBeanType();

            if (!declaringClass.getName().startsWith(APP_PACKAGE_PREFIX)) {
                continue; // not our controller - framework/library endpoint, out of scope
            }
            if (handler.getMethodAnnotation(RateLimited.class) != null) {
                continue; // tiered
            }

            uncovered.add("%s.%s()  %s".formatted(
                    declaringClass.getSimpleName(), handler.getMethod().getName(), entry.getKey()));
        }

        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("""
                    
                    Rate-limit coverage check failed, so the application will not start.
                    
                    Every application controller endpoint must declare a tier with @RateLimited, so a
                    new endpoint cannot go live unlimited by omission (FR-6.1).
                    
                    Endpoints missing @RateLimited:
                      %s
                    
                    Fix by adding @RateLimited(RateLimitTier.<TIER>) to each method above. If an
                    endpoint is genuinely exempt, this check is the place to say so explicitly, not
                    to silently skip it.
                    """.formatted(String.join("\n  ", uncovered)));
        }
    }
}