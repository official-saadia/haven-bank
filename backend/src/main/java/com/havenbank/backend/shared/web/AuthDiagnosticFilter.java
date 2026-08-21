package com.havenbank.backend.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TEMPORARY. Logs whether the security context actually survives in the session across the
 * password -> OTP -> authorize hand-off, which is the one fact that decides why sign-in bounces
 * back to the login page.
 *
 * <p>Runs ahead of the Spring Security filter chain, so the "before" reading shows what was
 * persisted by the <em>previous</em> request, and the "after" reading shows what this request
 * ended up with. Enable with {@code app.diagnostics.auth=true}; delete once the flow works.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.diagnostics", name = "auth", havingValue = "true")
public class AuthDiagnosticFilter extends OncePerRequestFilter {

    private static final String CTX_KEY = "SPRING_SECURITY_CONTEXT";
    private static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!isInteresting(path)) {
            chain.doFilter(request, response);
            return;
        }

        log.info("DIAG >> {} {} | {}", request.getMethod(), path, describe(request));
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("DIAG << {} {} | status={} location={} | {}",
                    request.getMethod(), path,
                    response.getStatus(),
                    response.getHeader("Location"),
                    describe(request));
        }
    }

    private boolean isInteresting(String path) {
        return path.startsWith("/login") || path.startsWith("/oauth2/authorize");
    }

    private String describe(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String sessionId = session == null ? "NO-SESSION" : session.getId();
        String inSession = "n/a";
        String savedRequest = "n/a";
        if (session != null) {
            Object stored = session.getAttribute(CTX_KEY);
            inSession = stored == null ? "ABSENT" : principalOf((SecurityContext) stored);
            savedRequest = session.getAttribute(SAVED_REQUEST) == null ? "ABSENT" : "PRESENT";
        }
        Authentication holder = SecurityContextHolder.getContext().getAuthentication();
        String inHolder = holder == null ? "null"
                : holder.getName() + "/auth=" + holder.isAuthenticated()
                + "/" + holder.getClass().getSimpleName();
        return "session=" + sessionId
                + " sessionContext=" + inSession
                + " savedRequest=" + savedRequest
                + " holder=" + inHolder;
    }

    private String principalOf(SecurityContext context) {
        Authentication auth = context.getAuthentication();
        return auth == null ? "EMPTY" : auth.getName() + "/auth=" + auth.isAuthenticated();
    }
}
