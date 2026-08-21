package com.havenbank.backend.authserver.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

/**
 * Shared security plumbing, kept apart from the filter-chain configurations.
 *
 * <p>These beans are consumed both by the chains and by {@code MfaAuthenticationSuccessHandler} and
 * {@code OtpController}, which write to them directly. Declaring them alongside a chain that also
 * injects those collaborators produces a dependency cycle, so they live here instead.
 */
@Slf4j
@Configuration
public class SecurityInfrastructureConfig {

    /**
     * One repository for every chain and for the MFA collaborators that save through it.
     * {@code HttpSessionSecurityContextRepository} decides whether to persist by comparing against
     * the context it loaded at the start of the request, so a separately constructed instance can
     * silently skip the write.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /**
     * Only cache requests worth resuming after sign-in.
     *
     * <p>The default cache stores whatever triggered the redirect to login, including things the
     * browser fetches on its own - {@code /.well-known/appspecific/com.chrome.devtools.json} when
     * DevTools is open, favicons, asset probes. Any of those silently replaces the real
     * {@code /oauth2/authorize} request, and sign-in then completes into a dead URL.
     */
    @Bean
    public RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(request -> {
            boolean save = shouldSave(request);
            log.debug("RequestCache: {} {} -> {}",
                    request.getMethod(), request.getRequestURI(), save ? "SAVED" : "skipped");
            return save;
        });
        return cache;
    }

    private static boolean shouldSave(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/.well-known/") || path.startsWith("/css/")
                || path.startsWith("/js/") || path.equals("/favicon.ico")) {
            return false;
        }
        // The authorization request is the whole point of the cache; keep it regardless of Accept.
        if (path.startsWith("/oauth2/authorize")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}